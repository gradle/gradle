/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.resource.transport.http.ntlm;

import jcifs.ntlmssp.Type1Message;
import jcifs.ntlmssp.Type2Message;
import jcifs.ntlmssp.Type3Message;
import jcifs.util.Base64;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.impl.auth.NTLMEngine;
import org.apache.http.impl.auth.NTLMEngineException;
import org.apache.http.impl.auth.NTLMScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;

import java.io.IOException;

// Copied from http://hc.apache.org/httpcomponents-client-ga/ntlm.html
public class NTLMSchemeFactory implements AuthSchemeFactory {

    public static void register(DefaultHttpClient httpClient) {
        httpClient.getAuthSchemes().register("ntlm", new NTLMSchemeFactory());
    }

    public AuthScheme newInstance(HttpParams params) {
        return new NTLMScheme(new JCIFSEngine());
    }

    private static class JCIFSEngine implements NTLMEngine {

        public String generateType1Msg(String domain, String workstation) throws NTLMEngineException {
            Type1Message type1Message = new Type1Message(Type1Message.getDefaultFlags(), domain, workstation);
            return Base64.encode(type1Message.toByteArray());
        }

        public String generateType3Msg(String username, String password, String domain, String workstation, String challenge) throws NTLMEngineException {
            Type2Message type2Message = decodeType2Message(challenge);
            Type3Message type3Message = new Type3Message(type2Message, password, domain, username, workstation, Type3Message.getDefaultFlags());
            return Base64.encode(type3Message.toByteArray());
        }

        private Type2Message decodeType2Message(String challenge) throws NTLMEngineException {
            try {
                return new Type2Message(Base64.decode(challenge));
            } catch (final IOException exception) {
                throw new NTLMEngineException("Invalid Type2 message", exception);
            }
        }
    }
}
