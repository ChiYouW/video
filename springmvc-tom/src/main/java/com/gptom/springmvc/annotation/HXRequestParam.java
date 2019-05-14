package com.gptom.springmvc.annotation;

import java.lang.annotation.*;

/**
 * @author wangxiansheng
 * @create 2019-05-14 11:36
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HXRequestParam {
    String value() default "";
}
