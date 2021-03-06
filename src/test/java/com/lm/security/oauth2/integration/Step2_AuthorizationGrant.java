package com.lm.security.oauth2.integration;


import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.lm.ShopifyEmbeddedAppSpringBootApplication;
import com.lm.security.authentication.CipherPassword;
import com.lm.security.oauth2.integration.config.DisabledShopifyVerfificationConfig;
import com.lm.security.oauth2.integration.config.TestConfig;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes= {ShopifyEmbeddedAppSpringBootApplication.class, TestConfig.class, DisabledShopifyVerfificationConfig.class})
@TestPropertySource(locations="classpath:test-application.properties")
@AutoConfigureMockMvc
public class Step2_AuthorizationGrant {

	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient;
	
	@Autowired
	private JdbcTemplate jdbc; 
	
	@Autowired
	private CipherPassword cipherPassword;
	
	private String SESSION_ATTRIBUTE_NAME = HttpSessionOAuth2AuthorizationRequestRepository.class.getName() +  ".AUTHORIZATION_REQUEST";
	
	private Map<String, OAuth2AuthorizationRequest> oAuth2AuthorizationRequests;
	
	private String CODE = "sample_code_returned_by_Shopify";
	private String HMAC = "da9d83c171400a41f8db91a950508985";
	private String TIMESTAMP = "1409617544";
	private String SHOP = "newstoretest.myshopify.com";

	
	/*
	 * Perform the initial Authorization request and grab objects stored in the HttpSession
	 * that will be used to "continue" the session in the test
	 */
	@SuppressWarnings("unchecked")
	@Before
	public void initializeValue() throws Exception {
		String sampleSalt = KeyGenerators.string().generateKey();
		TextEncryptor encryptor = Encryptors.queryableText(cipherPassword.getPassword(), sampleSalt);

		jdbc.update("UPDATE STOREACCESSTOKENS SET access_token=?, salt=? WHERE shop='lmdev.myshopify.com'", encryptor.encrypt("sample"), sampleSalt);
		
		MvcResult mR = this.mockMvc.perform(get("/install/shopify?shop=" + SHOP + "&timestamp=dsd&hmac=sdfasrf4324")).andReturn();

		HttpSession rSession = mR.getRequest().getSession();
		System.out.println(rSession);
		System.out.println(rSession.getAttributeNames().nextElement());
		
		oAuth2AuthorizationRequests = (Map<String, OAuth2AuthorizationRequest>) rSession.getAttribute(SESSION_ATTRIBUTE_NAME);
		System.out.println(oAuth2AuthorizationRequests);
	}
	

	/* 
	 * Test OAuth2LoginAuthenticationFilter prepares a POST request 
	 * by capturing the OAuth2AuthorizationCodeGrantRequest sent to 
	 * OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
	 * 
	 * Check:
	 * 
	 * 1. The OAuth2AccessTokenResponseClient mock was called
	 * 2. The clientId of the ClientRegistration matches the one in TestConfig
	 * 3. The state generated in @Before(AuthorizationRequest) is in OAuth2AuthorizationRequest
	 * 4. The code passed by "Shopify" in the url is in OAuth2AuthorizationRequest
	 * 5. The redirectUri in the OAuth2AuthorizationRequest 
	 * 
	 * If test is successful: the OAuth2AuthorizationCodeGrantRequest
	 * that the accessTokenResponseClient has all necessary, correct information
	 * to request the token from Shopify
	 * 
	 */
	@Test
	public void whenShopifyJSRedirectsThenObtainAuthenticationCode() throws Exception {
		
		
		// For the nonce Shopify requires, use the one generated previously by Spring
		Iterator<Entry<String, OAuth2AuthorizationRequest>> it = oAuth2AuthorizationRequests.entrySet().iterator();
		String state = it.next().getKey();
		
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(SESSION_ATTRIBUTE_NAME, oAuth2AuthorizationRequests);
		

		
		// configure mock accessTokenResponseClient (used by OAuth2LoginAuthenticationProvider's authenticate(auth))
		// the request itself should fail
		when(accessTokenResponseClient.getTokenResponse(ArgumentMatchers.any())).thenThrow(new OAuth2AuthorizationException(new OAuth2Error("502")));
		
		this.mockMvc.perform(get("/login/app/oauth2/code/shopify?code=" + CODE + "&hmac=" + HMAC + "&timestamp=" + TIMESTAMP + "&state=" + state + "&shop=" + SHOP).session(session)).andReturn();

		ArgumentCaptor<OAuth2AuthorizationCodeGrantRequest> grantRequest = ArgumentCaptor.forClass(OAuth2AuthorizationCodeGrantRequest.class);
		
		// ... But
		// assert...
		verify(accessTokenResponseClient).getTokenResponse(grantRequest.capture());
		
		OAuth2AuthorizationCodeGrantRequest capturedArg = grantRequest.getValue();
		ClientRegistration registration = capturedArg.getClientRegistration();
		OAuth2AuthorizationExchange authExch = capturedArg.getAuthorizationExchange();
		OAuth2AuthorizationRequest authReq = authExch.getAuthorizationRequest(); // from HttpSession
		OAuth2AuthorizationResponse authResp = authExch.getAuthorizationResponse();
		
		Pattern p = Pattern.compile(".*/login/app/oauth2/code/shopify");
		
		Assert.assertEquals("testId", registration.getClientId());
		Assert.assertEquals(state, authReq.getState());
		Assert.assertEquals(state, authResp.getState());
		Assert.assertEquals(CODE, authResp.getCode());
		
		Matcher matcher = p.matcher(authResp.getRedirectUri());
		Assert.assertTrue(matcher.matches());
		
		// The DefaultAuthorizationCodeTokenResponseClient takes care of preparing the POST
		
	}
	
	
	
	

}