package com.navio.apollo.casper

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter

/**
 * An Interceptor of HTTP requests that extracts a custom test header in order to set the current
 * CasperTestContext in order to request the appropriate test specific database.
 */
@Component
@Order(66)
class TestIsolatedDatabaseRoutingFilter : HandlerInterceptorAdapter() {
    companion object {
        val log = LoggerFactory.getLogger(TestIsolatedRoutingDataSource::class.java)
        const val TEST_DB_HEADER = "XX-TestDB"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val dbRequested = request.getHeader(TEST_DB_HEADER) ?: "".trim()
        log.debug("RECEIVED HEADER: $dbRequested")
        CasperTestContext.setState(DatabaseConfigurationState.IsolatedTestTemplate(dbRequested))
        return super.preHandle(request, response, handler)
    }
}
