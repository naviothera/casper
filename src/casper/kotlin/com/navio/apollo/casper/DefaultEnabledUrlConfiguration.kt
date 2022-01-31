package com.navio.apollo.casper

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration containing the urls that should be enabled for the ApiFilter defined
 * in the ApiFilterRegistration.
 */
@Configuration
class DefaultEnabledUrlConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun enabledUrls(): CasperEnabledUrls {
        return CasperEnabledUrls(listOf("/*"))
    }
}
