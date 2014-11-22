/*******************************************************************************
 *     Cloud Foundry 
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.oauth.token;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.Principal;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.jwt.crypto.sign.RsaVerifier;
import org.springframework.security.jwt.crypto.sign.SignatureVerifier;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * OAuth2 token services that produces JWT encoded token values.
 * 
 * @author Dave Syer
 * @author Luke Taylor
 * @author Joel D'sa
 */
@Controller
public class TokenKeyEndpoint implements InitializingBean {

    protected final Log logger = LogFactory.getLog(getClass());

    private SignerProvider signerProvider;

    /**
     * @param signerProvider the signerProvider to set
     */
    public void setSignerProvider(SignerProvider signerProvider) {
        this.signerProvider = signerProvider;
    }

    /**
     * Get the verification key for the token signatures. The principal has to
     * be provided only if the key is secret
     * (shared not public).
     * 
     * @param principal the currently authenticated user if there is one
     * @return the key used to verify tokens
     */
    @RequestMapping(value = "/token_key", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, String> getKey(Principal principal) {
        if ((principal == null || principal instanceof AnonymousAuthenticationToken) && !signerProvider.isPublic()) {
            throw new AccessDeniedException("You need to authenticate to see a shared key");
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("alg", signerProvider.getSigner().algorithm());
        result.put("value", signerProvider.getVerifierKey());
        //new values per OpenID and JWK spec
        result.put("kty", signerProvider.getType());
        result.put("use", "sig");
        if (signerProvider.isPublic() && "RSA".equals(signerProvider.getType())) {
            SignatureVerifier verifier = signerProvider.getVerifier();
            if (verifier!=null && verifier instanceof RsaVerifier) {
                RSAPublicKey rsaKey = extractRsaPublicKey((RsaVerifier) verifier) ;
                if (rsaKey!=null) {
                    String n = new String(Base64.encode(rsaKey.getModulus().toByteArray()));
                    String e = new String(Base64.encode(rsaKey.getPublicExponent().toByteArray()));
                    result.put("n", n);
                    result.put("e", e);
                }
            }
        }
        return result;
    }


    private RSAPublicKey extractRsaPublicKey(RsaVerifier verifier) {
        try {
            Field f = verifier.getClass().getDeclaredField("key");
            if (f != null) {
                f.setAccessible(true);
                if (f.get(verifier) instanceof RSAPublicKey) {
                    return (RSAPublicKey) f.get(verifier);
                }
            }
        } catch (NoSuchFieldException e) {

        } catch (IllegalAccessException e) {

        } catch (ClassCastException x) {

        }
        return null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.state(this.signerProvider != null, "A SignerProvider must be provided");
    }
}
