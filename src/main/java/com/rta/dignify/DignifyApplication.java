package com.rta.dignify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
public class DignifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(DignifyApplication.class, args);
	}

}
