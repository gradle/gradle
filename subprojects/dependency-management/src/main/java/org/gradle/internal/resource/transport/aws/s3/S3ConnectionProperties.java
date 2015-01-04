/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.resource.transport.aws.s3;

import com.google.common.base.Optional;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.resource.transport.http.HttpProxySettings;
import org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpProxySettings;
import org.gradle.internal.resource.transport.http.JavaSystemPropertiesSecureHttpProxySettings;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static com.amazonaws.services.s3.internal.Constants.S3_HOSTNAME;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.System.getProperty;
import static org.apache.commons.lang.StringUtils.isBlank;

public class S3ConnectionProperties {
    public static final String S3_ENDPOINT_PROPERTY = "org.gradle.s3.endpoint";
    private static final Set<String> SUPPORTED_SCHEMES = newHashSet("HTTP", "HTTPS");

    private final Optional<URI> endpoint;
    private final HttpProxySettings proxySettings;
    private final HttpProxySettings secureProxySettings;

    public S3ConnectionProperties() {
        endpoint = configureEndpoint(getProperty(S3_ENDPOINT_PROPERTY));
        proxySettings = new JavaSystemPropertiesHttpProxySettings();
        secureProxySettings = new JavaSystemPropertiesSecureHttpProxySettings();
    }

    public S3ConnectionProperties(HttpProxySettings proxySettings, HttpProxySettings secureProxySettings, URI endpoint) {
        this.endpoint = Optional.fromNullable(endpoint);
        this.proxySettings = proxySettings;
        this.secureProxySettings = secureProxySettings;
    }

    private Optional<URI> configureEndpoint(String property) {
        URI uri = null;
        if (StringUtils.isNotBlank(property)) {
            try {
                uri = new URI(property);
                if (isBlank(uri.getScheme()) || !SUPPORTED_SCHEMES.contains(uri.getScheme().toUpperCase())) {
                    throw new IllegalArgumentException("System property [" + S3_ENDPOINT_PROPERTY + "=" + property + "] must have a scheme of 'http' or 'https'");
                }
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("System property [" + S3_ENDPOINT_PROPERTY + "=" + property + "]  must be a valid URI");
            }
        }
        return Optional.fromNullable(uri);
    }

    public Optional<URI> getEndpoint() {
        return endpoint;
    }

    public Optional<HttpProxySettings.HttpProxy> getProxy() {
        if (endpoint.isPresent()) {
            String host = endpoint.get().getHost();
            if (endpoint.get().getScheme().toUpperCase().equals("HTTP")) {
                return Optional.fromNullable(proxySettings.getProxy(host));
            } else {
                return Optional.fromNullable(secureProxySettings.getProxy(host));
            }
        }
        return Optional.fromNullable(secureProxySettings.getProxy(S3_HOSTNAME));
    }
}
