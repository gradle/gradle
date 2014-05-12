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


import org.gradle.internal.resource.PasswordCredentials;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NTLMCredentials {
    private static final String DEFAULT_DOMAIN = "";
    private static final String DEFAULT_WORKSTATION = "";
    private final String domain;
    private final String username;
    private final String password;
    private final String workstation;

    public NTLMCredentials(PasswordCredentials credentials) {
        String domain;
        String username = credentials.getUsername();
        int slashPos = username.indexOf('\\');
        slashPos = slashPos >= 0 ? slashPos : username.indexOf('/');
        if (slashPos >= 0) {
            domain = username.substring(0, slashPos);
            username = username.substring(slashPos + 1);
        } else {
            domain = System.getProperty("http.auth.ntlm.domain", DEFAULT_DOMAIN);
        }
        this.domain = domain == null ? null : domain.toUpperCase();
        this.username = username;
        this.password = credentials.getPassword();
        this.workstation = determineWorkstationName();
    }

    private String determineWorkstationName() {
        // TODO:DAZ This is a temporary (hidden) property that may be useful to track down issues. Remove when NTLM Auth is solid.
        String sysPropWorkstation = System.getProperty("http.auth.ntlm.workstation");
        if (sysPropWorkstation != null) {
            return sysPropWorkstation;
        }

        try {
            return removeDotSuffix(getHostName()).toUpperCase();
        } catch (UnknownHostException e) {
            return DEFAULT_WORKSTATION;
        }
    }

    protected String getHostName() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostName();
    }

    private String removeDotSuffix(String val) {
        int dotPos = val.indexOf('.');
        return dotPos == -1 ? val : val.substring(0, dotPos);
    }


    public String getDomain() {
        return domain;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getWorkstation() {
        return workstation;
    }

    @Override
    public String toString() {
        return String.format("NTLM Credentials [user: %s, domain: %s, workstation: %s]", username, domain, workstation);
    }
}
