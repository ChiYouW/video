package com.gptom.springmvc.annotation;

import java.lang.annotation.*;

/**
 * @author wangxiansheng
 * @create 2019-05-14 11:33
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HXController {

    String value() default "";

}