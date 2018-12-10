package com.lm.security.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/*
 * This success handler wraps the default SavedRequestAwareAuthenticationSuccessHandler
 * so that it will perform as intended, but without the redirect support.
 * Thus, the DefaultRedirectStrategy is replaced with an empty implementation.
 */
public class NoRedirectSuccessHandler implements AuthenticationSuccessHandler {
	
	private SavedRequestAwareAuthenticationSuccessHandler defaultHandler;
	
	public NoRedirectSuccessHandler() {
		this.defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();
		this.defaultHandler.setRedirectStrategy((i,j,k) -> { });
		
		
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		
		defaultHandler.onAuthenticationSuccess(request, response, authentication);

		
	}

}
