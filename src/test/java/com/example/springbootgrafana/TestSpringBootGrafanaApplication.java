package com.example.springbootgrafana;

import org.springframework.boot.SpringApplication;

public class TestSpringBootGrafanaApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringBootGrafanaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
