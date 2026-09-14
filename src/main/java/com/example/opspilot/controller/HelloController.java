package com.example.opspilot.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.opspilot.service.HelloService;

import lombok.RequiredArgsConstructor;


@RequestMapping("/hello")
@RequiredArgsConstructor 
@RestController
public class HelloController {

    private final HelloService helloService;

    @RequestMapping("/")
    public String getHello() {
        return helloService.hello();
    }
}
