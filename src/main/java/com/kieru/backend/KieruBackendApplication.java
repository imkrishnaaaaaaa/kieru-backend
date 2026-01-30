package com.kieru.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})@EnableAsync      // Enables the @Async thread pool
@EnableScheduling // Enables @Scheduled (for the nightly stats job later)
public class KieruBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KieruBackendApplication.class, args);
        System.out.println("\n\n ------------------------------------------ " +
                "\n| *** Application Started Successfully *** |\n" +
                " ------------------------------------------ \n\n");

	}

}