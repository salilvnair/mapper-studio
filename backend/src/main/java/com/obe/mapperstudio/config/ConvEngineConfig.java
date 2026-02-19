package com.obe.mapperstudio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.salilvnair.api.processor.rest.facade.RestWebServiceFacade;
import com.github.salilvnair.ccf.annotation.EnableCcfCore;
import com.github.salilvnair.convengine.annotation.EnableConvEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConvEngine(stream = false)
@EnableCcfCore
public class ConvEngineConfig {
    @Bean
    public RestWebServiceFacade restWebServiceFacade() {
        return new RestWebServiceFacade();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
