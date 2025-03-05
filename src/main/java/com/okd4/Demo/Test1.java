package com.okd4.Demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Test1 {
	@GetMapping("/hello")
	public String hello_func() {
		return "hello v3";
	}
}
