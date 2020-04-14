package com.navio.apollo

import com.coxautodev.graphql.tools.PerFieldObjectMapperProvider
import com.coxautodev.graphql.tools.SchemaParser
import com.coxautodev.graphql.tools.SchemaParserOptions
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.navio.apollo.graphql.MutationResolver
import com.navio.apollo.graphql.QueryResolver
import graphql.servlet.core.GraphQLErrorHandler
import graphql.servlet.core.GraphQLObjectMapper
import javax.inject.Inject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

private val logger = LoggerFactory.getLogger(GraphQLConfiguration::class.java)

@Configuration
@AutoConfigureAfter(Configuration::class)
class GraphQLConfiguration(
    @Inject
    val queryResolver: QueryResolver,

    @Inject
    val mutationResolver: MutationResolver,

    @Inject
    val mapperBuilder: Jackson2ObjectMapperBuilder
) {

    companion object {
        val SCHEMA_FILES = arrayOf(
            "base.graphqls"
        )
    }

    @Autowired
    lateinit var errorHandler: GraphQLErrorHandler

    @Bean
    fun getObjectMapper(): GraphQLObjectMapper {
        return object : GraphQLObjectMapper({
            val mapper: ObjectMapper = mapperBuilder.build()
            val module = SimpleModule()
            mapper.registerModule(module)
            mapper
        }, { errorHandler }) {
        }
    }

    @Bean
    fun getSchemaParser(): SchemaParser {
        logger.info("creating SchemaParser!")
        val options = SchemaParserOptions.newOptions()
            .objectMapperProvider(PerFieldObjectMapperProvider { getObjectMapper().jacksonMapper }).build()

        return SchemaParser
            .newParser()
            .files(*SCHEMA_FILES)
            .resolvers(queryResolver)
            .resolvers(mutationResolver)
            .options(options)
            .build()
    }
}
