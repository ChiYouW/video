package com.gptom.springmvc.demo.action;

import com.gptom.springmvc.annotation.*;
import com.gptom.springmvc.demo.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * @author wangxiansheng
 * @create 2019-05-14 13:35
 */
@HXController
@HXRequestMapping("/demo")
public class DemoAction {

    @HXAutowired
    private IDemoService demoService;

    @HXRequestMapping("/query.json")
    public void query(HttpServletRequest request, HttpServletResponse response, @HXRequestParam("name") String name) {
        try {
            request.setCharacterEncoding("utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String info = demoService.findInfo(name);
        try {
            response.setContentType("text/html;charset=utf-8");
            response.setCharacterEncoding("utf-8");
            response.getWriter().write(info);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
