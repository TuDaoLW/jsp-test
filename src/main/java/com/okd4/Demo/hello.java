package com.okd4.Demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/api")
public class hello {
    @GetMapping("/hello")
    public String hello_func(){
        return "java spring";
    }
}
