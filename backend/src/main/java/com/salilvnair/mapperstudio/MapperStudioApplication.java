package com.salilvnair.mapperstudio;

import com.salilvnair.mapperstudio.bootstrap.SqliteBootstrapInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(
        basePackages = {"com.salilvnair.mapperstudio"}
)
//@EntityScan(
//        basePackages = {"com.github.salilvnair.convengdemo.entity"}
//)
//@EnableJpaRepositories(
//        basePackages = {"com.github.salilvnair.convengdemo.repo"}
//)
public class MapperStudioApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MapperStudioApplication.class);
        app.addInitializers(new SqliteBootstrapInitializer());
        app.run(args);
    }
}
