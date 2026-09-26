package it.coinquilini;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale di avvio dell'applicazione Spring Boot.
 * Riferimento slide: Architecture of a Web Application - Application Server.
 */
@SpringBootApplication
public class CoinquiliniApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoinquiliniApplication.class, args);
    }
}
