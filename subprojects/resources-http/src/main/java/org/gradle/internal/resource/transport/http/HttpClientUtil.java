/*
 * Copyright 2021 the original author or authors.
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
import org.apache.http.HttpResponse;
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
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.util.PublicSuffixMatcher;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.cookie.DefaultCookieSpecProvider;
import org.apache.http.impl.cookie.IgnoreSpecProvider;
import org.apache.http.impl.cookie.NetscapeDraftSpecProvider;
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.gradle.api.JavaVersion;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.specs.Spec;
import org.gradle.authentication.Authentication;
import org.gradle.authentication.http.BasicAuthentication;
import org.gradle.authentication.http.DigestAuthentication;
import org.gradle.authentication.http.HttpHeaderAuthentication;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.resource.transport.http.ntlm.NTLMCredentials;
import org.gradle.internal.resource.transport.http.ntlm.NTLMSchemeFactory;
import org.gradle.util.internal.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.apache.http.client.protocol.HttpClientContext.REDIRECT_LOCATIONS;

/**
 * Houses constructs shared between sync client and async client.
 */
public class HttpClientUtil {

    public static final int MAX_HTTP_CONNECTIONS = 20;
    private static final String HTTPS_PROTOCOLS = "https.protocols";

    private HttpClientUtil() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtil.class);

    /**
     * Strips the {@link URI#getUserInfo() user info} from the {@link URI} making it
     * safe to appear in log messages.
     */
    public static URI stripUserCredentials(URI uri) {
        try {
            return new URIBuilder(uri).setUserInfo(null).build();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e, true);
        }
    }

    @Nullable
    public static URI effectiveUri(HttpUriRequest request, HttpContext httpContext) {
        @SuppressWarnings("unchecked")
        List<URI> redirects = (List<URI>) httpContext.getAttribute(REDIRECT_LOCATIONS);
        if (redirects == null || redirects.isEmpty()) {
            return request.getURI();
        } else {
            return redirects.get(redirects.size() - 1);
        }
    }

    public static HttpRequestException toHttpRequestException(HttpUriRequest request, URI effectiveUri, IOException e, DocumentationRegistry documentationRegistry) {
        Exception cause = e;
        if (e instanceof SSLHandshakeException) {
            SSLHandshakeException sslException = (SSLHandshakeException) e;
            final String confidence;
            if (sslException.getMessage() != null && sslException.getMessage().contains("protocol_version")) {
                // If we're handling an SSLHandshakeException with the error of 'protocol_version' we know that the server doesn't support this protocol.
                confidence = "The server does not";
            } else {
                // Sometimes the SSLHandshakeException doesn't include the 'protocol_version', even though this is the cause of the error.
                // Tell the user this but with less confidence.
                confidence = "The server may not";
            }
            String message = String.format(
                confidence + " support the client's requested TLS protocol versions: (%s). " +
                    "You may need to configure the client to allow other protocols to be used. " +
                    "See: %s",
                String.join(", ", supportedTlsVersions()),
                documentationRegistry.getDocumentationFor("build_environment", "gradle_system_properties")
            );
            cause = new HttpRequestException(message, cause);
        }

        return new HttpRequestException(String.format("Could not %s '%s'.", request.getMethod(), effectiveUri), cause);
    }

    public static void useCredentials(CredentialsProvider credentialsProvider, Authentication authentication) {
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

    private static String getAuthScheme(Authentication authentication) {
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

    public static void configureProxy(CredentialsProvider credentialsProvider, HttpSettings httpSettings) {
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
    }

    static void useCredentials(CredentialsProvider credentialsProvider, Collection<? extends Authentication> authentications) {
        for (Authentication authentication : authentications) {
            useCredentials(credentialsProvider, authentication);
        }
    }

    public static CookieConfig createCookieConfig() {
        PublicSuffixMatcher publicSuffixMatcher = PublicSuffixMatcherLoader.getDefault();
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
        Registry<CookieSpecProvider> cookieSpecRegistry = RegistryBuilder.<CookieSpecProvider>create()
            .register(CookieSpecs.DEFAULT, defaultProvider)
            .register("best-match", defaultProvider)
            .register("compatibility", defaultProvider)
            .register(CookieSpecs.STANDARD, laxStandardProvider)
            .register(CookieSpecs.STANDARD_STRICT, strictStandardProvider)
            .register(CookieSpecs.NETSCAPE, new NetscapeDraftSpecProvider())
            .register(CookieSpecs.IGNORE_COOKIES, new IgnoreSpecProvider())
            .build();

        return new CookieConfig(publicSuffixMatcher, cookieSpecRegistry);
    }

    public static PreemptiveAuth createPreemptiveAuthInterceptor(Collection<Authentication> authentications) {
        return new PreemptiveAuth(getAuthScheme(authentications), isPreemptiveEnabled(authentications));
    }

    private static AuthScheme getAuthScheme(final Collection<Authentication> authentications) {
        if (authentications.size() == 1) {
            if (authentications.iterator().next() instanceof HttpHeaderAuthentication) {
                return new HttpHeaderAuthScheme();
            }
        }
        return new BasicScheme();
    }

    private static boolean isPreemptiveEnabled(Collection<Authentication> authentications) {
        return CollectionUtils.any(authentications, new Spec<Authentication>() {
            @Override
            public boolean isSatisfiedBy(Authentication element) {
                return element instanceof BasicAuthentication || element instanceof HttpHeaderAuthentication;
            }
        });
    }

    public static RequestConfig createDefaultRequestConfig(HttpSettings httpSettings) {
        HttpTimeoutSettings timeoutSettings = httpSettings.getTimeoutSettings();
        return RequestConfig.custom()
            .setConnectTimeout(timeoutSettings.getConnectionTimeoutMs())
            .setSocketTimeout(timeoutSettings.getSocketTimeoutMs())
            .setMaxRedirects(httpSettings.getMaxRedirects())
            .build();
    }

    public static RedirectStrategy createRedirectStrategy(HttpSettings httpSettings) {
        if (httpSettings.getMaxRedirects() > 0) {
            return new RedirectVerifyingStrategyDecorator(getBaseRedirectStrategy(httpSettings), httpSettings.getRedirectVerifier());
        } else {
            return new RedirectStrategy() {
                @Override
                public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                    return false;
                }

                @Override
                public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private static RedirectStrategy getBaseRedirectStrategy(HttpSettings httpSettings) {
        switch (httpSettings.getRedirectMethodHandlingStrategy()) {
            case ALLOW_FOLLOW_FOR_MUTATIONS:
                return new AllowFollowForMutatingMethodRedirectStrategy();
            case ALWAYS_FOLLOW_AND_PRESERVE:
                return new AlwaysFollowAndPreserveMethodRedirectStrategy();
            default:
                throw new IllegalArgumentException(httpSettings.getRedirectMethodHandlingStrategy().name());
        }
    }

    public static Registry<AuthSchemeProvider> createAuthSchemeRegistry() {
        return RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.BASIC, new BasicSchemeFactory())
            .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
            .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
            .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
            .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())
            .register(HttpHeaderAuthScheme.AUTH_SCHEME_NAME, new HttpHeaderSchemeFactory())
            .build();
    }

    /**
     * Determines the HTTPS protocols to support for the client.
     *
     * @implNote To support the Gradle embedded test runner, this method's return value should not be cached in a static field.
     */
    public static List<String> supportedTlsVersions() {
        /*
         * System property retrieval is executed within the constructor to support the Gradle embedded test runner.
         */
        String httpsProtocols = System.getProperty(HTTPS_PROTOCOLS);
        if (httpsProtocols != null) {
            return Arrays.asList(httpsProtocols.split(","));
        } else if (JavaVersion.current().isJava8() && Jvm.current().isIbmJvm()) {
            return Collections.singletonList("TLSv1.2");
        } else if (jdkSupportsTLSProtocol("TLSv1.3")) {
            return Arrays.asList("TLSv1.2", "TLSv1.3");
        } else {
            return Collections.singletonList("TLSv1.2");
        }
    }

    private static boolean jdkSupportsTLSProtocol(@SuppressWarnings("SameParameterValue") final String protocol) {
        try {
            for (String supportedProtocol : SSLContext.getDefault().getSupportedSSLParameters().getProtocols()) {
                if (protocol.equals(supportedProtocol)) {
                    return true;
                }
            }
            return false;
        } catch (NoSuchAlgorithmException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static final class CookieConfig {
        public final PublicSuffixMatcher publicSuffixMatcher;
        public final Registry<CookieSpecProvider> cookieSpecRegistry;

        public CookieConfig(PublicSuffixMatcher publicSuffixMatcher, Registry<CookieSpecProvider> cookieSpecRegistry) {
            this.publicSuffixMatcher = publicSuffixMatcher;
            this.cookieSpecRegistry = cookieSpecRegistry;
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
