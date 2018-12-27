package com.lm.security.filters;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.lm.security.authentication.ShopifyOriginToken;
import com.lm.security.authentication.ShopifyVerificationStrategy;

/*
 * This filter checks the request to see if it came from Shopify.
 * It only checks the paths passed in via the constructor (matchedPaths and the restrictedPath)
 * 
 * If the request matches the authorizationPath, it must be from Shopify and contain the valid nonce.
 * If not, it uses accessDeniedHandler to generate an error
 * 
 * For any other matching path, if it came from Shopify, and it isn't already OAuth2 authenticated, it populates 
 * the SecurityContext with a ShopifyOriginToken.
 * 
 * If not, the ShopifyOriginToken is not set.
 * 
 * 
 */
public class ShopifyOriginFilter implements Filter {

	private AntPathRequestMatcher mustComeFromShopifyMatcher;
	private List<AntPathRequestMatcher> applicablePaths;
	private ShopifyVerificationStrategy shopifyVerificationStrategy;
	private AccessDeniedHandler accessDeniedHandler = new AccessDeniedHandlerImpl();
	
	
	public ShopifyOriginFilter(ShopifyVerificationStrategy shopifyVerificationStrategy, String authorizationPath, String... matchedPaths) {
		this.mustComeFromShopifyMatcher = new AntPathRequestMatcher(authorizationPath);
		this.shopifyVerificationStrategy = shopifyVerificationStrategy;
		
		applicablePaths = new ArrayList<>();
		applicablePaths.add(mustComeFromShopifyMatcher);
		Arrays.stream(matchedPaths).forEach(i -> applicablePaths.add(new AntPathRequestMatcher(i)));
		
	}
	
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		boolean mustBeFromShopify = false;
		boolean comesFromShopify = false;
		boolean isAlreadyAuthenticated = false;
		
		if(!applyFilter(request)) {
			chain.doFilter(request, response);
			
			return;
		}
		
		// this filter will be applied
		mustBeFromShopify = mustComeFromShopifyMatcher.matches((HttpServletRequest)request);
		comesFromShopify = isShopifyRequest(request);
		isAlreadyAuthenticated = isAlreadyAuthenticated();
				
		if(mustBeFromShopify) {

			if(comesFromShopify && hasValidNonce(request)) {

				if(!isAlreadyAuthenticated) {

					SecurityContextHolder.getContext().setAuthentication(new ShopifyOriginToken(true));
				}
			} else {
				// do not set any Authentication
				// the path must be .authenticated() 
				accessDeniedHandler.handle((HttpServletRequest)request, (HttpServletResponse)response, new AccessDeniedException("This request must come from Shopify"));
				return;
			}
			
		} else {

			if(comesFromShopify) {

				if(!isAlreadyAuthenticated) {
					SecurityContextHolder.getContext().setAuthentication(new ShopifyOriginToken(true));
				}
			}

		}
		
		chain.doFilter(request, response);

	}
	
	/*
	 * 
	 * Uses ShopifyVerificationStrategy to...
	 * 
	 * 1. Remove hmac parameter from query string
	 * 2. Build query string
	 * 3. HMAC-SHA256(query)
	 * 4. Is (3) = hmac value?
	 * 
	 */
	private boolean isShopifyRequest(ServletRequest request) {
		return shopifyVerificationStrategy.isShopifyRequest((HttpServletRequest)request);

	}
	
	private boolean hasValidNonce(ServletRequest request) {
		return shopifyVerificationStrategy.hasValidNonce((HttpServletRequest)request);

	}
	
	private boolean isAlreadyAuthenticated() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		
		if(auth instanceof OAuth2AuthenticationToken) {
			return true;
		}
		
		return false;

	}
	
	// should apply the filter if the request matches
	// any path passed in to this filter
	private boolean applyFilter(ServletRequest request) {
		HttpServletRequest req = (HttpServletRequest)request;
		
		boolean match = this.applicablePaths.stream().anyMatch(i -> i.matches(req));
		
		return match;
		
	}
	
}