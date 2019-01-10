package com.lm.security.service;

import java.util.Set;


import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import com.lm.security.authentication.CipherPassword;
import com.lm.security.configuration.SecurityBeansConfig;
import com.lm.security.repository.EncryptedTokenAndSalt;
import com.lm.security.repository.TokenRepository;
import com.lm.security.repository.TokenRepository.OAuth2AccessTokenWithSalt;


public class TokenService {
	
	public static final String SHOP_ATTRIBUTE_NAME = "shop";
	
	private TokenRepository tokenRepository;
	private CipherPassword cipherPassword;
	private ClientRegistrationRepository clientRepository;
	
	public TokenService(TokenRepository tokenRepository, CipherPassword cipherPassword, ClientRegistrationRepository clientRepository) {
		this.tokenRepository = tokenRepository;
		this.cipherPassword = cipherPassword;
		this.clientRepository = clientRepository;

	}
	
	public void saveNewStore(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
		
		String shop = getStoreName(principal);
		
		Set<String> scopes = authorizedClient.getAccessToken().getScopes();
		
		EncryptedTokenAndSalt encryptedTokenAndSalt = getTokenAndSalt(authorizedClient);

		this.tokenRepository.saveNewStore(shop, scopes, encryptedTokenAndSalt);	
		
	}
	
	// returns true if a store with this name exists, regardless of validity of stored credentials
	public boolean doesStoreExist(String shop) {
		OAuth2AccessTokenWithSalt token = this.tokenRepository.findTokenForRequest(shop);
		
		if(token != null) {
			return true;
		}
		
		return false;
	}

	// will return an existing, valid store
	public OAuth2AuthorizedClient getStore(String shopName) {
		
		OAuth2AccessTokenWithSalt ets = this.tokenRepository.findTokenForRequest(shopName);
		
		if(ets == null) {
			return null;
		}
		
		OAuth2AccessToken rawToken = getRawToken(ets);
		
		if(rawToken == null) {
			// the salt and encrypted passwords are out of date
			return null;
		}
		
		ClientRegistration cr = clientRepository.findByRegistrationId(SecurityBeansConfig.SHOPIFY_REGISTRATION_ID);
		
		if(cr == null) {
			throw new RuntimeException("An error occurred retrieving the ClientRegistration for " + SecurityBeansConfig.SHOPIFY_REGISTRATION_ID);
		}
		
		return new OAuth2AuthorizedClient(
				cr,
				shopName,
				rawToken,
				null);
		
	}
	
	
	
	
	
	public void updateStore(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
		
		String shop = getStoreName(principal);
		
		EncryptedTokenAndSalt encryptedTokenAndSalt = getTokenAndSalt(authorizedClient);
		
		this.tokenRepository.updateKey(shop, encryptedTokenAndSalt);

	}
	
	public void uninstallStore(String store) {
		this.tokenRepository.uninstallStore(store);
	}
	
	
	
	private String getStoreName(Authentication principal) {
		String shop = ((OAuth2AuthenticationToken)principal).getPrincipal().getName();

		return shop;
	}
	
	private OAuth2AccessToken getRawToken(OAuth2AccessTokenWithSalt toS) {
		String salt = toS.getSalt();
		
		OAuth2AccessToken enTok = toS.getAccess_token();
		String decryptedToken = decryptToken(new EncryptedTokenAndSalt(enTok.getTokenValue(), salt));
		
		return new OAuth2AccessToken(enTok.getTokenType(),
									 decryptedToken,
									 enTok.getIssuedAt(),
									 enTok.getExpiresAt(),
									 enTok.getScopes());
	}
	
	private EncryptedTokenAndSalt getTokenAndSalt(OAuth2AuthorizedClient authorizedClient) {
		
		String rawAccessTokenValue = authorizedClient.getAccessToken().getTokenValue();
		
		String genSalt = KeyGenerators.string().generateKey();
		
		TextEncryptor encryptor = Encryptors.queryableText(cipherPassword.getPassword(), genSalt);
		
		return new EncryptedTokenAndSalt(encryptor.encrypt(rawAccessTokenValue), genSalt);
		
	}
	
	
	private String decryptToken(EncryptedTokenAndSalt enC) {
		TextEncryptor textEncryptor = Encryptors.queryableText(cipherPassword.getPassword(), enC.getSalt());
		
		String decryptedToken = null;
		try {
			decryptedToken = textEncryptor.decrypt(enC.getEncryptedToken());
		} catch(Exception e) {
			// the cipher password changed...
			
		}
		return decryptedToken;
		
		
	}
	

}

