package springJdbc;

import springIoc.annotation.ComponentScan;
import springIoc.annotation.Configuration;
import springIoc.annotation.Import;
import springJdbc.jdbc.JdbcConfiguration;

@Import(JdbcConfiguration.class)
@ComponentScan
@Configuration
public class AppConfig {
}