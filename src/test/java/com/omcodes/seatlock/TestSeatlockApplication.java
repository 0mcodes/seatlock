package com.omcodes.seatlock;

import org.springframework.boot.SpringApplication;

public class TestSeatlockApplication {

	public static void main(String[] args) {
		SpringApplication.from(SeatlockApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
