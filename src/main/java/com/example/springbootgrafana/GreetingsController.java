package com.example.springbootgrafana;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingsController {

	static Logger logger = LoggerFactory.getLogger(GreetingsController.class);

	@GetMapping("/greetings")
	public String greetings() {
		logger.atInfo().addKeyValue("foo", "bar").log("request...");
		return "Hello, World!";
	}

}
