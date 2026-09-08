package com.lifehub;

import com.lifehub.api.common.AppPaths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LifeHubApplication {

    public static void main(String[] args) {
        AppPaths.prepareDirectories(args, System.getenv());
        SpringApplication.run(LifeHubApplication.class, args);
    }
}
