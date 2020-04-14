package com.navio.apollo

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator
import javax.servlet.Filter
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.web.client.RestTemplate

@Configuration
@EnableJpaRepositories(
    "com.navio.apollo.repositories"
)
@EnableTransactionManagement
@EnableCaching
class Configuration {

    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        return builder.build()
    }

    @Bean
    @Primary
    fun objectMapperBuilder(handlerInstantiator: HandlerInstantiator): Jackson2ObjectMapperBuilder {
        val builder = Jackson2ObjectMapperBuilder()
        builder.handlerInstantiator(handlerInstantiator)
        builder.featuresToEnable(SerializationFeature.INDENT_OUTPUT)
        return builder
    }

    /**
     * Register the [OpenEntityManagerInViewFilter] so that the
     * GraphQL-Servlet can handle lazy loads during execution.
     *
     * @see https://stackoverflow.com/a/51999356
     */
    @Bean
    fun filter(): Filter {
        return OpenEntityManagerInViewFilter()
    }
}
