package com.project.waternet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WaternetApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaternetApplication.class, args);
	}

}
