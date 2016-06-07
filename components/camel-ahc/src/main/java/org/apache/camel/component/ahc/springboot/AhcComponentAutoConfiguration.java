package org.apache.camel.component.ahc.springboot;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.component.ahc.AhcComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
@EnableConfigurationProperties(AhcComponentConfiguration.class)
public class AhcComponentAutoConfiguration {

	@Bean
	@ConditionalOnClass(CamelContext.class)
	@ConditionalOnMissingBean(AhcComponent.class)
	public AhcComponent configureComponent(CamelContext camelContext,
			AhcComponentConfiguration configuration) throws Exception {
		AhcComponent component = new AhcComponent();
		component.setCamelContext(camelContext);
		Map<String, Object> parameters = new HashMap<>();
		IntrospectionSupport.getProperties(configuration, parameters, null);
		IntrospectionSupport.setProperties(camelContext,
				camelContext.getTypeConverter(), component, parameters);
		return component;
	}
}