package com.example.opspilot.service;

import org.springframework.stereotype.Service;

import com.example.opspilot.repository.HelloRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor 
@Service 
public class HelloService {
    
    private final HelloRepository helloRepository;

    public String hello() {
        return "hello";
    }
}
