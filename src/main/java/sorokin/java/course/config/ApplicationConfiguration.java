package sorokin.java.course.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import java.util.Scanner;

@Configuration
@PropertySource("classpath:application.properties")
@Import(HibernateConfiguration.class)
public class ApplicationConfiguration {

    @Bean
    public Scanner scanner() {
        return new Scanner(System.in);
    }

}
