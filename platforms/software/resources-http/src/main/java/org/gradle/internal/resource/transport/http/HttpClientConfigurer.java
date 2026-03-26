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

import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.KerberosSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SPNegoSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.gradle.api.JavaVersion;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.authentication.Authentication;
import org.gradle.authentication.http.BasicAuthentication;
import org.gradle.authentication.http.DigestAuthentication;
import org.gradle.authentication.http.HttpHeaderAuthentication;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.resource.UriTextResource;
import org.gradle.internal.resource.transport.http.ntlm.NTLMCredentials;
import org.gradle.internal.resource.transport.http.ntlm.NTLMSchemeFactory;
import org.gradle.util.internal.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class HttpClientConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientConfigurer.class);
    private static final String HTTPS_PROTOCOLS = "https.protocols";

    /**
     * Determines the HTTPS protocols to support for the client.
     *
     * @implNote To support the Gradle embedded test runner, this method's return value should not be cached in a static field.
     */
    private String[] determineHttpsProtocols() {
        /*
         * System property retrieval is executed within the constructor to support the Gradle embedded test runner.
         */
        String httpsProtocols = System.getProperty(HTTPS_PROTOCOLS);
        if (httpsProtocols != null) {
            return httpsProtocols.split(",");
        } else if (JavaVersion.current().isJava8() && Jvm.current().isIbmJvm()) {
            return new String[]{"TLSv1.2"};
        } else if (jdkSupportsTLSProtocol("TLSv1.3")) {
            return new String[]{"TLSv1.2", "TLSv1.3"};
        } else {
            return new String[]{"TLSv1.2"};
        }
    }

    private boolean jdkSupportsTLSProtocol(@SuppressWarnings("SameParameterValue") final String protocol) {
        try {
            for (String supportedProtocol : httpSettings.getSslContextFactory().createSslContext().getSupportedSSLParameters().getProtocols()) {
                if (protocol.equals(supportedProtocol)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    Collection<String> supportedTlsVersions() {
        return Arrays.asList(sslProtocols);
    }

    private final String[] sslProtocols;
    private final HttpSettings httpSettings;

    public HttpClientConfigurer(HttpSettings httpSettings) {
        this.httpSettings = httpSettings;
        this.sslProtocols = determineHttpsProtocols();
    }

    public void configure(HttpClientBuilder builder) {
        SystemDefaultCredentialsProvider credentialsProvider = new SystemDefaultCredentialsProvider();
        configureConnectionManager(builder, httpSettings.getSslContextFactory(), httpSettings.getHostnameVerifier());
        configureAuthSchemeRegistry(builder);
        configureCredentials(builder, credentialsProvider, httpSettings.getAuthenticationSettings());
        configureProxy(builder, credentialsProvider, httpSettings);
        configureUserAgent(builder);
        configureRequestConfig(builder);
        configureRedirectStrategy(builder);
        builder.setDefaultCredentialsProvider(credentialsProvider);
    }

    private void configureConnectionManager(HttpClientBuilder builder, SslContextFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
        HttpTimeoutSettings timeoutSettings = httpSettings.getTimeoutSettings();
        builder.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContextFactory.createSslContext(), sslProtocols, null, hostnameVerifier))
            .setMaxConnTotal(httpSettings.getMaxConnTotal())
            .setMaxConnPerRoute(httpSettings.getMaxConnPerRoute())
            .setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(timeoutSettings.getSocketTimeoutMs()))
                .setSoKeepAlive(true)
                .build())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setTimeToLive(TimeValue.ofMilliseconds(timeoutSettings.getIdleConnectionTimeoutMs()))
                .build())
            .build());
    }

    private void configureAuthSchemeRegistry(HttpClientBuilder builder) {
        builder.setDefaultAuthSchemeRegistry(RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.BASIC, new BasicSchemeFactory())
            .register(StandardAuthScheme.DIGEST, new DigestSchemeFactory())
            .register(StandardAuthScheme.NTLM, new NTLMSchemeFactory())
            .register(StandardAuthScheme.SPNEGO, SPNegoSchemeFactory.DEFAULT)
            .register(StandardAuthScheme.KERBEROS, KerberosSchemeFactory.DEFAULT)
            .register(HttpHeaderAuthScheme.AUTH_SCHEME_NAME, new HttpHeaderSchemeFactory())
            .build()
        );
    }

    private void configureCredentials(HttpClientBuilder builder, CredentialsStore credentialsProvider, Collection<Authentication> authentications) {
        if (authentications.size() > 0) {
            useCredentials(credentialsProvider, authentications);

            // Use preemptive authorisation if no other authorisation has been established
            builder.addRequestInterceptorFirst(new PreemptiveAuth(getAuthScheme(authentications), isPreemptiveEnabled(authentications)));
        }
    }

    private AuthScheme getAuthScheme(final Collection<Authentication> authentications) {
        if (authentications.size() == 1) {
            if (authentications.iterator().next() instanceof HttpHeaderAuthentication) {
                return new HttpHeaderAuthScheme();
            }
        }
        return new BasicScheme();
    }

    private void configureProxy(HttpClientBuilder builder, CredentialsStore credentialsProvider, HttpSettings httpSettings) {
        useCredentialsForProxy(credentialsProvider, httpSettings.getProxySettings().getProxy());
        useCredentialsForProxy(credentialsProvider, httpSettings.getSecureProxySettings().getProxy());

        builder.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()));
    }

    private static boolean hasProxyCredentials(HttpSettings httpSettings) {
        HttpProxySettings.HttpProxy httpProxy = httpSettings.getProxySettings().getProxy();
        HttpProxySettings.HttpProxy httpsProxy = httpSettings.getSecureProxySettings().getProxy();
        return (httpProxy != null && httpProxy.credentials != null)
            || (httpsProxy != null && httpsProxy.credentials != null);
    }

    private void useCredentialsForProxy(CredentialsStore credentialsProvider, HttpProxySettings.HttpProxy httpsProxy) {
        if (httpsProxy != null && httpsProxy.credentials != null) {
            AllSchemesAuthentication authentication1 = new AllSchemesAuthentication(httpsProxy.credentials);
            authentication1.addHost(httpsProxy.host, httpsProxy.port);
            useCredentials(credentialsProvider, Collections.singleton(authentication1));
        }
    }

    private void useCredentials(CredentialsStore credentialsProvider, Collection<? extends Authentication> authentications) {
        for (Authentication authentication : authentications) {
            AuthenticationInternal authenticationInternal = (AuthenticationInternal) authentication;

            String scheme = getAuthScheme(authentication);
            org.gradle.api.credentials.Credentials credentials = authenticationInternal.getCredentials();

            Collection<AuthenticationInternal.HostAndPort> hostsForAuthentication = authenticationInternal.getHostsForAuthentication();
            assert !hostsForAuthentication.isEmpty() : "Credentials and authentication required for a HTTP repository, but no hosts were defined for the authentication?";

            for (AuthenticationInternal.HostAndPort hostAndPort : hostsForAuthentication) {
                String host = hostAndPort.getHost();
                int port = hostAndPort.getPort();

                assert host != null : "HTTP credentials and authentication require a host scope to be defined as well";

                if (credentials instanceof HttpHeaderCredentials) {
                    HttpHeaderCredentials httpHeaderCredentials = (HttpHeaderCredentials) credentials;
                    Credentials httpCredentials = new HttpClientHttpHeaderCredentials(httpHeaderCredentials.getName(), httpHeaderCredentials.getValue());
                    credentialsProvider.setCredentials(new AuthScope(null, host, port, null, scheme), httpCredentials);

                    LOGGER.debug("Using {} for authenticating against '{}:{}' using {}", httpHeaderCredentials, host, port, scheme);
                } else if (credentials instanceof PasswordCredentials || credentials instanceof HttpProxySettings.HttpProxyCredentials) {
                    String username;
                    String password;
                    if (credentials instanceof PasswordCredentials) {
                        PasswordCredentials passwordCredentials = (PasswordCredentials) credentials;
                        username = passwordCredentials.getUsername();
                        password = passwordCredentials.getPassword();
                    } else {
                        HttpProxySettings.HttpProxyCredentials proxyCredentials = (HttpProxySettings.HttpProxyCredentials) credentials;
                        username = proxyCredentials.getUsername();
                        password = proxyCredentials.getPassword();
                    }

                    if (authentication instanceof AllSchemesAuthentication) {
                        NTLMCredentials ntlmCredentials = new NTLMCredentials(username, password);
                        Credentials httpCredentials = new NTCredentials(ntlmCredentials.getUsername(), ntlmCredentials.getPassword() != null ? ntlmCredentials.getPassword().toCharArray() : null, ntlmCredentials.getWorkstation(), ntlmCredentials.getDomain());
                        credentialsProvider.setCredentials(new AuthScope(null, host, port, null, StandardAuthScheme.NTLM), httpCredentials);

                        LOGGER.debug("Using {} and {} for authenticating against '{}:{}' using {}", credentials, ntlmCredentials, host, port, StandardAuthScheme.NTLM);
                    }

                    Credentials httpCredentials = new UsernamePasswordCredentials(username, password != null ? password.toCharArray() : null);
                    credentialsProvider.setCredentials(new AuthScope(null, host, port, null, scheme), httpCredentials);
                    LOGGER.debug("Using {} for authenticating against '{}:{}' using {}", credentials, host, port, scheme);
                } else {
                    throw new IllegalArgumentException(String.format("Credentials must be an instance of: %s or %s", PasswordCredentials.class.getCanonicalName(), HttpHeaderCredentials.class.getCanonicalName()));
                }
            }
        }
    }

    private boolean isPreemptiveEnabled(Collection<Authentication> authentications) {
        return CollectionUtils.any(authentications, element -> element instanceof BasicAuthentication || element instanceof HttpHeaderAuthentication);
    }

    public void configureUserAgent(HttpClientBuilder builder) {
        builder.setUserAgent(UriTextResource.getUserAgentString());
    }

    private void configureRequestConfig(HttpClientBuilder builder) {
        HttpTimeoutSettings timeoutSettings = httpSettings.getTimeoutSettings();
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(timeoutSettings.getConnectionTimeoutMs()))
            .setResponseTimeout(Timeout.ofMilliseconds(timeoutSettings.getSocketTimeoutMs()))
            .setMaxRedirects(httpSettings.getMaxRedirects())
            .setExpectContinueEnabled(hasProxyCredentials(httpSettings))
            .setCookieSpec(StandardCookieSpec.RELAXED)
            .build();
        builder.setDefaultRequestConfig(config);
    }

    private void configureRedirectStrategy(HttpClientBuilder builder) {
        if (httpSettings.getMaxRedirects() > 0) {
            builder.setRedirectStrategy(new RedirectVerifyingStrategyDecorator(getBaseRedirectStrategy(), httpSettings.getRedirectVerifier()));
        } else {
            builder.disableRedirectHandling();
        }
    }

    private RedirectStrategy getBaseRedirectStrategy() {
        switch (httpSettings.getRedirectMethodHandlingStrategy()) {
            case ALLOW_FOLLOW_FOR_MUTATIONS:
                return new AllowFollowForMutatingMethodRedirectStrategy();
            case ALWAYS_FOLLOW_AND_PRESERVE:
                return new AlwaysFollowAndPreserveMethodRedirectStrategy();
            default:
                throw new IllegalArgumentException(httpSettings.getRedirectMethodHandlingStrategy().name());
        }
    }

    private String getAuthScheme(Authentication authentication) {
        if (authentication instanceof BasicAuthentication) {
            return StandardAuthScheme.BASIC;
        } else if (authentication instanceof DigestAuthentication) {
            return StandardAuthScheme.DIGEST;
        } else if (authentication instanceof HttpHeaderAuthentication) {
            return HttpHeaderAuthScheme.AUTH_SCHEME_NAME;
        } else if (authentication instanceof AllSchemesAuthentication) {
            return null;
        } else {
            throw new IllegalArgumentException(String.format("Authentication scheme of '%s' is not supported.", authentication.getClass().getSimpleName()));
        }
    }

    static class PreemptiveAuth implements HttpRequestInterceptor {
        private final AuthScheme authScheme;
        private final boolean alwaysSendAuth;

        PreemptiveAuth(AuthScheme authScheme, boolean alwaysSendAuth) {
            this.authScheme = authScheme;
            this.alwaysSendAuth = alwaysSendAuth;
        }

        @Override
        public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context) throws HttpException {
            HttpClientContext clientContext = HttpClientContext.cast(context);

            try {
                URI uri = request.getUri();
                HttpHost targetHost = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
                AuthExchange authExchange = clientContext.getAuthExchange(targetHost);

                if (authExchange.getState() != AuthExchange.State.UNCHALLENGED) {
                    return;
                }

                // If no authExchange has been established and this is a PUT or POST request, add preemptive authorisation
                String requestMethod = request.getMethod();
                if (alwaysSendAuth || requestMethod.equals(HttpPut.METHOD_NAME) || requestMethod.equals(HttpPost.METHOD_NAME)) {
                    authExchange.select(authScheme);
                }
            } catch (URISyntaxException e) {
                // Cannot determine target host, skip preemptive auth
            }
        }
    }

}
