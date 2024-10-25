package springWeb;


import jakarta.servlet.ServletContext;
import springIoc.annotation.Autowired;
import springIoc.annotation.Bean;
import springIoc.annotation.Configuration;
import springIoc.annotation.Value;

import java.util.Objects;

@Configuration
public class WebMvcConfiguration {

    private static ServletContext servletContext = null;

    /**
     * Set by web listener.
     */
    public static void setServletContext(ServletContext ctx) {
        servletContext = ctx;
    }

    @Bean(initMethod = "init")
    ViewResolver viewResolver( //
                               @Autowired ServletContext servletContext, //
                               @Value("${summer.web.freemarker.template-path:/WEB-INF/templates}") String templatePath, //
                               @Value("${summer.web.freemarker.template-encoding:UTF-8}") String templateEncoding) {
        return new FreeMarkerViewResolver(servletContext, templatePath, templateEncoding);
    }

    @Bean
    ServletContext servletContext() {
        return Objects.requireNonNull(servletContext, "ServletContext is not set.");
    }
}

