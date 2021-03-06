package com.lm.security.oauth2.integration.config;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.lm.security.authentication.ShopifyVerificationStrategy;

@Configuration
public class DisabledShopifyVerfificationConfig {
	@Bean
	@Primary
	public ShopifyVerificationStrategy shopifyVerficationStrategy() {
		return new NullVerificationStrategy();
	}
	
	
	class NullVerificationStrategy extends ShopifyVerificationStrategy {
		
		public NullVerificationStrategy() {
			super(null,null);
		}
		
		@Override
		public boolean isShopifyRequest(HttpServletRequest request) {
			return true;
		}
		
		@Override
		public boolean hasValidNonce(HttpServletRequest request) {
			return true;
		}
		
		@Override
		public boolean isHeaderShopifyRequest(HttpServletRequest request, String registrationId) {
			return true;
		}
		
		
	}

}
