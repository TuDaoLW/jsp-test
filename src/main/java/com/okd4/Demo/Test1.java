package com.okd4.Demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Test1 {
	@GetMapping("/")
	public String hello_func() {
		return "nhà còn mỗi cái máng lợn";
	}
}
