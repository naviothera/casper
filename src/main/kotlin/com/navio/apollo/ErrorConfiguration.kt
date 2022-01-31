package com.navio.apollo

import graphql.ExceptionWhileDataFetching
import graphql.GraphQLError
import graphql.kickstart.execution.error.DefaultGraphQLErrorHandler
import graphql.kickstart.execution.error.GraphQLErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ErrorConfiguration {

    @Bean
    fun errorHandler(): GraphQLErrorHandler {
        return CustomGraphQLErrorHandler()
    }

    class CustomGraphQLErrorHandler : DefaultGraphQLErrorHandler() {
        override fun isClientError(error: GraphQLError?): Boolean {
            if (error is ExceptionWhileDataFetching) return true
            return super.isClientError(error)
        }
    }
}
