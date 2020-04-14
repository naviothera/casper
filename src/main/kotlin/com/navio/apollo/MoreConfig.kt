package com.navio.apollo

import com.fasterxml.jackson.databind.cfg.HandlerInstantiator
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.SpringHandlerInstantiator

@Configuration
class MoreConfig {
    @Bean
    fun handlerInstantiator(applicationContext: ApplicationContext): HandlerInstantiator {
        return SpringHandlerInstantiator(applicationContext.getAutowireCapableBeanFactory())
    }
}
