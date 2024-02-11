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

import com.google.common.collect.Lists;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.PublicSuffixMatcher;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.impl.cookie.DefaultCookieSpecProvider;
import org.apache.http.impl.cookie.IgnoreSpecProvider;
import org.apache.http.impl.cookie.NetscapeDraftSpecProvider;
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.gradle.api.JavaVersion;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.specs.Spec;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

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
        configureSslSocketConnectionFactory(builder, httpSettings.getSslContextFactory(), httpSettings.getHostnameVerifier());
        configureAuthSchemeRegistry(builder);
        configureCredentials(builder, credentialsProvider, httpSettings.getAuthenticationSettings());
        configureProxy(builder, credentialsProvider, httpSettings);
        configureUserAgent(builder);
        configureCookieSpecRegistry(builder);
        configureRequestConfig(builder);
        configureSocketConfig(builder);
        configureRedirectStrategy(builder);
        builder.setDefaultCredentialsProvider(credentialsProvider);
        builder.setMaxConnTotal(httpSettings.getMaxConnTotal());
        builder.setMaxConnPerRoute(httpSettings.getMaxConnPerRoute());
        builder.setConnectionTimeToLive(httpSettings.getTimeoutSettings().getIdleConnectionTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    private void configureSslSocketConnectionFactory(HttpClientBuilder builder, SslContextFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
        builder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContextFactory.createSslContext(), sslProtocols, null, hostnameVerifier));
    }

    private void configureAuthSchemeRegistry(HttpClientBuilder builder) {
        builder.setDefaultAuthSchemeRegistry(RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.BASIC, new BasicSchemeFactory())
            .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
            .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
            .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
            .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())
            .register(HttpHeaderAuthScheme.AUTH_SCHEME_NAME, new HttpHeaderSchemeFactory())
            .build()
        );
    }

    private void configureCredentials(HttpClientBuilder builder, CredentialsProvider credentialsProvider, Collection<Authentication> authentications) {
        if (authentications.size() > 0) {
            useCredentials(credentialsProvider, authentications);

            // Use preemptive authorisation if no other authorisation has been established
            builder.addInterceptorFirst(new PreemptiveAuth(getAuthScheme(authentications), isPreemptiveEnabled(authentications)));
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

    private void configureProxy(HttpClientBuilder builder, CredentialsProvider credentialsProvider, HttpSettings httpSettings) {
        HttpProxySettings.HttpProxy httpProxy = httpSettings.getProxySettings().getProxy();
        HttpProxySettings.HttpProxy httpsProxy = httpSettings.getSecureProxySettings().getProxy();

        for (HttpProxySettings.HttpProxy proxy : Lists.newArrayList(httpProxy, httpsProxy)) {
            if (proxy != null) {
                if (proxy.credentials != null) {
                    AllSchemesAuthentication authentication = new AllSchemesAuthentication(proxy.credentials);
                    authentication.addHost(proxy.host, proxy.port);
                    useCredentials(credentialsProvider, Collections.singleton(authentication));
                }
            }
        }
        builder.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()));
    }

    private void useCredentials(CredentialsProvider credentialsProvider, Collection<? extends Authentication> authentications) {
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
                    credentialsProvider.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM, scheme), httpCredentials);

                    LOGGER.debug("Using {} for authenticating against '{}:{}' using {}", httpHeaderCredentials, host, port, scheme);
                } else if (credentials instanceof PasswordCredentials) {
                    PasswordCredentials passwordCredentials = (PasswordCredentials) credentials;

                    if (authentication instanceof AllSchemesAuthentication) {
                        NTLMCredentials ntlmCredentials = new NTLMCredentials(passwordCredentials);
                        Credentials httpCredentials = new NTCredentials(ntlmCredentials.getUsername(), ntlmCredentials.getPassword(), ntlmCredentials.getWorkstation(), ntlmCredentials.getDomain());
                        credentialsProvider.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM, AuthSchemes.NTLM), httpCredentials);

                        LOGGER.debug("Using {} and {} for authenticating against '{}:{}' using {}", passwordCredentials, ntlmCredentials, host, port, AuthSchemes.NTLM);
                    }

                    Credentials httpCredentials = new UsernamePasswordCredentials(passwordCredentials.getUsername(), passwordCredentials.getPassword());
                    credentialsProvider.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM, scheme), httpCredentials);
                    LOGGER.debug("Using {} for authenticating against '{}:{}' using {}", passwordCredentials, host, port, scheme);
                } else {
                    throw new IllegalArgumentException(String.format("Credentials must be an instance of: %s or %s", PasswordCredentials.class.getCanonicalName(), HttpHeaderCredentials.class.getCanonicalName()));
                }
            }
        }
    }

    private boolean isPreemptiveEnabled(Collection<Authentication> authentications) {
        return CollectionUtils.any(authentications, new Spec<Authentication>() {
            @Override
            public boolean isSatisfiedBy(Authentication element) {
                return element instanceof BasicAuthentication || element instanceof HttpHeaderAuthentication;
            }
        });
    }

    public void configureUserAgent(HttpClientBuilder builder) {
        builder.setUserAgent(UriTextResource.getUserAgentString());
    }

    private void configureCookieSpecRegistry(HttpClientBuilder builder) {
        PublicSuffixMatcher publicSuffixMatcher = PublicSuffixMatcherLoader.getDefault();
        builder.setPublicSuffixMatcher(publicSuffixMatcher);
        // Add more data patterns to the default configuration to work around https://github.com/gradle/gradle/issues/1596
        final CookieSpecProvider defaultProvider = new DefaultCookieSpecProvider(DefaultCookieSpecProvider.CompatibilityLevel.DEFAULT, publicSuffixMatcher, new String[]{
            "EEE, dd-MMM-yy HH:mm:ss z", // Netscape expires pattern
            DateUtils.PATTERN_RFC1036,
            DateUtils.PATTERN_ASCTIME,
            DateUtils.PATTERN_RFC1123
        }, false);
        final CookieSpecProvider laxStandardProvider = new RFC6265CookieSpecProvider(
            RFC6265CookieSpecProvider.CompatibilityLevel.RELAXED, publicSuffixMatcher);
        final CookieSpecProvider strictStandardProvider = new RFC6265CookieSpecProvider(
            RFC6265CookieSpecProvider.CompatibilityLevel.STRICT, publicSuffixMatcher);
        builder.setDefaultCookieSpecRegistry(RegistryBuilder.<CookieSpecProvider>create()
            .register(CookieSpecs.DEFAULT, defaultProvider)
            .register("best-match", defaultProvider)
            .register("compatibility", defaultProvider)
            .register(CookieSpecs.STANDARD, laxStandardProvider)
            .register(CookieSpecs.STANDARD_STRICT, strictStandardProvider)
            .register(CookieSpecs.NETSCAPE, new NetscapeDraftSpecProvider())
            .register(CookieSpecs.IGNORE_COOKIES, new IgnoreSpecProvider())
            .build()
        );
    }

    private void configureRequestConfig(HttpClientBuilder builder) {
        HttpTimeoutSettings timeoutSettings = httpSettings.getTimeoutSettings();
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(timeoutSettings.getConnectionTimeoutMs())
            .setSocketTimeout(timeoutSettings.getSocketTimeoutMs())
            .setMaxRedirects(httpSettings.getMaxRedirects())
            .build();
        builder.setDefaultRequestConfig(config);
    }

    private void configureSocketConfig(HttpClientBuilder builder) {
        HttpTimeoutSettings timeoutSettings = httpSettings.getTimeoutSettings();
        builder.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(timeoutSettings.getSocketTimeoutMs()).setSoKeepAlive(true).build());
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
            return AuthSchemes.BASIC;
        } else if (authentication instanceof DigestAuthentication) {
            return AuthSchemes.DIGEST;
        } else if (authentication instanceof HttpHeaderAuthentication) {
            return HttpHeaderAuthScheme.AUTH_SCHEME_NAME;
        } else if (authentication instanceof AllSchemesAuthentication) {
            return AuthScope.ANY_SCHEME;
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
        public void process(final HttpRequest request, final HttpContext context) throws HttpException {

            AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

            if (authState.getAuthScheme() != null || authState.hasAuthOptions()) {
                return;
            }

            // If no authState has been established and this is a PUT or POST request, add preemptive authorisation
            String requestMethod = request.getRequestLine().getMethod();
            if (alwaysSendAuth || requestMethod.equals(HttpPut.METHOD_NAME) || requestMethod.equals(HttpPost.METHOD_NAME)) {
                CredentialsProvider credentialsProvider = (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
                Credentials credentials = credentialsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                if (credentials != null) {
                    authState.update(authScheme, credentials);
                }
            }
        }
    }

}
