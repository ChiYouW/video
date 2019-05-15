package com.gptom.springmvc.servlet;

import com.gptom.springmvc.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wangxiansheng
 * @create 2019-05-14 10:55
 */
public class HXDispatcherServlet extends HttpServlet {


    private Properties properties = new Properties();
    private List<String> classNames = new ArrayList<String>();
    private Map<String, Object> ioc = new HashMap<String, Object>();
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("init...");

        // 1. 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2. 初始化所有的相关联的类,扫描用户设定的包下面的所有的类
        doScanner(properties.getProperty("scanPackage"));

        // 3. 把这些扫描到的类通过反射机制,实现初始化,并且放在IOC容器之中(Map)
        doInstance();

        // 4. 实现依赖注入
        // 要给加上了HXAutowirde注解的字段,哪怕是私有的字段
        doAutowried();

        // 5.初始化HandlerMapping(将url和对应的method进行关联)
        initHandlerMapping();

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception,Detaile : \r\n" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        Handler handler = getHandler(req);
        if (handler == null) {
            // 如果没有匹配上,返回404
            resp.getWriter().write("404 Not Found!");
            return;
        }
        // 获取方法参数列表
        Class<?>[] paramTypes = handler.method.getParameterTypes();

        // 保存所有需要自动赋值的参数值
        Object[] paramValues = new Object[paramTypes.length];

        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
            //如果找到匹配的对象,则开始填充参数值
            if (!handler.paramIndexMapping.containsKey(param.getKey())) {
                continue;
            }
            int index = handler.paramIndexMapping.get(param.getKey());
            paramValues[index] = convert(paramTypes[index], value);
        }

        //设置方法中的request和response对象
        int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = req;
        int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        paramValues[respIndex] = resp;

        handler.method.invoke(handler.controller, paramValues);
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

    private Handler getHandler(HttpServletRequest request) throws Exception {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            try {
                Matcher matcher = handler.pattern.matcher(url);
                if (!matcher.matches()) {
                    continue;
                }
                return handler;
            } catch (Exception e) {
                throw e;
            }
        }
        return null;
    }

    /**
     * 加载配置文件
     *
     * @param location 配置文件路径
     */
    private void doLoadConfig(String location) {
        //获取文件
        InputStream resource = this.getClass().getClassLoader().getResourceAsStream(location.split(":")[1]);
        try {
            properties.load(resource);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != resource) {
                try {
                    resource.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 扫描配置中所有关联的类
     *
     * @param packageName 包名称
     */
    private void doScanner(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                String classname = packageName + "." + file.getName().replace(".class", "");
                classNames.add(classname);
            }
        }
    }

    /**
     * 将扫描到的类通过反射,进行实例化,并且放在ioc容器中
     */
    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }

        classNames.forEach(className -> {
            try {
                Class<?> clazz = Class.forName(className);
                // 反射实现实例化
                // 只有加了我们注解的类需要实例化
                if (clazz.isAnnotationPresent(HXController.class)) {
                    String beanName = lowerFirst(clazz.getSimpleName());
                    //clazz.newInstance(); - 实例化
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(HXService.class)) {
                    HXService service = clazz.getAnnotation(HXService.class);

                    //1. 如果自己设置了一个名字,就要用自己的名字优先
                    String beanName = service.value();

                    //2. 如果自己没有设置名字,默认首字母小写
                    if ("".equals(beanName)) {
                        beanName = lowerFirst(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                    //3. 如果autoWride标注的是一个接口,默认要将其实现类的实例注入进来
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> anInterface : interfaces) {
                        ioc.put(anInterface.getName(), instance);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 实现依赖注入,给所有加了autowired的字段赋值
     */
    private void doAutowried() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 扫描所有字段,看有没有AutoWried注解
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(HXAutowired.class)) {
                    continue;
                }
                HXAutowired annotation = field.getAnnotation(HXAutowired.class);
                String beanName = annotation.value();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化handlerMapping,将所有的路径和对应的方法进行关联
     */
    private void initHandlerMapping() {

        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(HXController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(HXRequestMapping.class)) {
                HXRequestMapping requestMapping = clazz.getAnnotation(HXRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(HXRequestMapping.class)) {
                    continue;
                }
                HXRequestMapping requestMapping = method.getAnnotation(HXRequestMapping.class);
                String regex = ("/" + baseUrl + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("Mapping: " + regex + "," + method);
            }
        }
    }

    /**
     * 类名一定要大字母开头
     *
     * @param name 类名称
     * @return 首字母小写
     */
    private String lowerFirst(String name) {
        char[] chars = name.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 内部类,封装路径,路径对应的类,方法,以及参数的顺序
     */
    private class Handler {
        protected Object controller; //保存方法对应的实例

        protected Method method; // 保存映射的方法

        private Pattern pattern;  // URL

        private Map<String, Integer> paramIndexMapping; //参数排序

        protected Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            // 提取方法中加了注解的参数
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation annotation : pa[i]) {
                    if (annotation instanceof HXRequestParam) {
                        String paramName = ((HXRequestParam) annotation).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }

    }

}
