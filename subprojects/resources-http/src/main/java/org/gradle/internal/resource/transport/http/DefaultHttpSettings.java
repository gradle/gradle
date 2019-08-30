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


import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.gradle.authentication.Authentication;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.verifier.HttpRedirectVerifier;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collection;

public class DefaultHttpSettings implements HttpSettings {
    private final Collection<Authentication> authenticationSettings;
    private final SslContextFactory sslContextFactory;
    private final HostnameVerifier hostnameVerifier;
    private final HttpRedirectVerifier redirectVerifier;
    private final boolean followRedirects;


    private HttpProxySettings proxySettings;
    private HttpProxySettings secureProxySettings;
    private HttpTimeoutSettings timeoutSettings;

    public static Builder builder() {
        return new Builder();
    }

    private DefaultHttpSettings(Collection<Authentication> authenticationSettings, SslContextFactory sslContextFactory, HostnameVerifier hostnameVerifier, HttpRedirectVerifier redirectVerifier, boolean followRedirects) {
        this.followRedirects = followRedirects;
        Preconditions.checkNotNull(authenticationSettings, "authenticationSettings");
        Preconditions.checkNotNull(sslContextFactory, "sslContextFactory");
        Preconditions.checkNotNull(hostnameVerifier, "hostnameVerifier");
        Preconditions.checkNotNull(redirectVerifier, "redirectVerifier");

        this.authenticationSettings = authenticationSettings;
        this.sslContextFactory = sslContextFactory;
        this.hostnameVerifier = hostnameVerifier;
        this.redirectVerifier = redirectVerifier;
    }

    @Override
    public HttpProxySettings getProxySettings() {
        if (proxySettings == null) {
            proxySettings = new JavaSystemPropertiesHttpProxySettings();
        }
        return proxySettings;
    }

    @Override
    public HttpProxySettings getSecureProxySettings() {
        if (secureProxySettings == null) {
            secureProxySettings = new JavaSystemPropertiesSecureHttpProxySettings();
        }
        return secureProxySettings;
    }

    @Override
    public HttpTimeoutSettings getTimeoutSettings() {
        if (timeoutSettings == null) {
            timeoutSettings = new JavaSystemPropertiesHttpTimeoutSettings();
        }
        return timeoutSettings;
    }

    @Override
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    @Override
    public HttpRedirectVerifier getRedirectVerifier() {
        return redirectVerifier;
    }

    @Override
    public Collection<Authentication> getAuthenticationSettings() {
        return authenticationSettings;
    }

    @Override
    public SslContextFactory getSslContextFactory() {
        return sslContextFactory;
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public static class Builder {
        private Collection<Authentication> authenticationSettings;
        private SslContextFactory sslContextFactory;
        private HostnameVerifier hostnameVerifier;
        private HttpRedirectVerifier redirectVerifier;
        private boolean followRedirects = true;

        public Builder withAuthenticationSettings(Collection<Authentication> authenticationSettings) {
            this.authenticationSettings = authenticationSettings;
            return this;
        }

        public Builder withSslContextFactory(SslContextFactory sslContextFactory) {
            this.sslContextFactory = sslContextFactory;
            this.hostnameVerifier = new DefaultHostnameVerifier(null);
            return this;
        }

        public Builder withRedirectVerifier(HttpRedirectVerifier redirectVerifier) {
            this.redirectVerifier = redirectVerifier;
            return this;
        }

        public Builder allowUntrustedConnections() {
            this.sslContextFactory = ALL_TRUSTING_SSL_CONTEXT_FACTORY;
            this.hostnameVerifier = ALL_TRUSTING_HOSTNAME_VERIFIER;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public HttpSettings build() {
            return new DefaultHttpSettings(authenticationSettings, sslContextFactory, hostnameVerifier, redirectVerifier, followRedirects);
        }
    }

    private static final HostnameVerifier ALL_TRUSTING_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    private static final SslContextFactory ALL_TRUSTING_SSL_CONTEXT_FACTORY = new SslContextFactory() {
        private final Supplier<SSLContext> sslContextSupplier = Suppliers.memoize(new Supplier<SSLContext>() {
            @Override
            public SSLContext get() {
                try {
                    SSLContext sslcontext = SSLContext.getInstance("TLS");
                    sslcontext.init(null, allTrustingTrustManager, null);
                    return sslcontext;
                } catch (GeneralSecurityException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });

        @Override
        public SSLContext createSslContext() {
            return sslContextSupplier.get();
        }

        private final TrustManager[] allTrustingTrustManager = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };
    };

}
