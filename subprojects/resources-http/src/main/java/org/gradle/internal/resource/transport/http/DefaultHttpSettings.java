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
package org.gradle.internal.resource.transport.http;


import org.gradle.authentication.Authentication;

import java.util.Collection;

public class DefaultHttpSettings implements HttpSettings {
    private final HttpProxySettings proxySettings = new JavaSystemPropertiesHttpProxySettings();
    private final HttpProxySettings secureProxySettings = new JavaSystemPropertiesSecureHttpProxySettings();
    private final Collection<Authentication> authenticationSettings;
    private final SslContextFactory sslContextFactory;

    public DefaultHttpSettings(Collection<Authentication> authenticationSettings, SslContextFactory sslContextFactory) {
        if (authenticationSettings == null) {
            throw new IllegalArgumentException("Authentication settings cannot be null.");
        }

        this.authenticationSettings = authenticationSettings;
        this.sslContextFactory = sslContextFactory;
    }

    @Override
    public HttpProxySettings getProxySettings() {
        return proxySettings;
    }

    @Override
    public HttpProxySettings getSecureProxySettings() {
        return secureProxySettings;
    }

    @Override
    public Collection<Authentication> getAuthenticationSettings() {
        return authenticationSettings;
    }

    @Override
    public SslContextFactory getSslContextFactory() {
        return sslContextFactory;
    }
}
