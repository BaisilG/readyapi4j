package com.smartbear.readyapi.client.auth;

import io.swagger.client.model.Authentication;

import static com.smartbear.readyapi.client.Validator.validateNotEmpty;

public class NTLMAuthenticationBuilder extends BasicAuthenticationBuilder implements AuthenticationBuilderWithDomain {

    public NTLMAuthenticationBuilder(String username, String password) {
        super(username, password);
    }

    @Override
    public NTLMAuthenticationBuilder setDomain(String domain) {
        authentication.setDomain(domain);
        return this;
    }

    @Override
    public Authentication build() {
        validateNotEmpty(authentication.getUsername(), "Missing username, it's a required parameter for NTLM Auth.");
        validateNotEmpty(authentication.getPassword(), "Missing password, it's a required parameter for NTLM Auth.");
        authentication.setType("NTLM");
        return authentication;
    }
}