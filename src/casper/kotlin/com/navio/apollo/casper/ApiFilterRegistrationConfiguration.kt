package com.navio.apollo.casper

import javax.inject.Inject
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.ClassUtils
import org.springframework.web.filter.GenericFilterBean
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Simple data class for holding the collection of urls that will be enabled for the CasperApiInterceptorFilter.
 * Default registration is for all urls handled by the servlet container per the DefaultEnabledUrlConfiguration.
 */
data class CasperEnabledUrls(val urls: Collection<String>)

/**
 * Configuration class used to register the CasperApiInterceptorFilter instance with the Servlet container
 * for some set of Url patterns.
 */
@Configuration
class ApiFilterRegistrationConfiguration @Autowired constructor(private val factory: AutowireCapableBeanFactory) : WebMvcConfigurer {

    @Inject
    lateinit var enabledUrls: CasperEnabledUrls

    override fun configureDefaultServletHandling(configurer: DefaultServletHandlerConfigurer) {
        configurer.enable("default")
    }

    @Bean
    fun apiFilter(): FilterRegistrationBean<CasperApiInterceptorFilter> {
        return FilterRegistrationBean<CasperApiInterceptorFilter>().let {
            CasperApiInterceptorFilter().let { filter ->
                factory.autowireBean(filter)
                it.filter = filter
            }
            it.setUrlPatterns(enabledUrls.urls)
            it.setName(CasperApiInterceptorFilter::class.java.simpleName)
            it.order = 66
            it
        }
    }

    /**
     * Base GenericFilterBean used to intercept requests to the registered endpoint(s) and route them to the
     * defined filters; specifically the CasperDatabaseRoutingFilter.
     */
    class CasperApiInterceptorFilter : GenericFilterBean() {

        override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
            val apiInterceptor = TestIsolatedDatabaseRoutingFilter()
            val handler = HandlerMethod(
                EmptyHandler(),
                ClassUtils.getMethod(
                    EmptyHandler::class.java,
                    "handle"
                )
            )
            apiInterceptor.preHandle(request as HttpServletRequest, response as HttpServletResponse, handler)
            chain?.doFilter(request, response)
            apiInterceptor.afterCompletion(request, response, handler, null)
        }

        class EmptyHandler {
            fun handle() {}
        }
    }
}
