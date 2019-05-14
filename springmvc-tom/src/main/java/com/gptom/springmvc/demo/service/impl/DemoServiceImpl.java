package com.gptom.springmvc.demo.service.impl;

import com.gptom.springmvc.annotation.HXService;
import com.gptom.springmvc.demo.service.IDemoService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wangxiansheng
 * @create 2019-05-14 13:36
 */
@HXService
public class DemoServiceImpl implements IDemoService {


    @Override
    public String findInfo(String name) {
        return "Hello: " + name;
    }
}
