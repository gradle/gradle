/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport.gcs;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static java.lang.System.getProperty;

@SuppressWarnings("Duplicates") // re-use not possible across modules currently
final class GcsConnectionProperties {

    private static final String GCS_ENDPOINT_PROPERTY = "org.gradle.gcs.endpoint";
    private static final String GCS_SERVICE_PATH_PROPERTY = "org.gradle.gcs.servicePath";
    private static final Set<String> SUPPORTED_SCHEMES = Sets.newHashSet("HTTP", "HTTPS");

    private final URI endpoint;
    private final String servicePath;

    GcsConnectionProperties() {
        this(configureEndpoint(getProperty(GCS_ENDPOINT_PROPERTY)), configureServicePath(getProperty(GCS_SERVICE_PATH_PROPERTY)));
    }

    GcsConnectionProperties(URI endpoint, String servicePath) {
        this.endpoint = endpoint;
        this.servicePath = servicePath;
    }

    Optional<URI> getEndpoint() {
        return Optional.fromNullable(endpoint);
    }

    Optional<String> getServicePath() {
        return Optional.fromNullable(servicePath);
    }

    private static URI configureEndpoint(String property) {
        URI uri = null;
        if (StringUtils.isNotBlank(property)) {
            try {
                uri = new URI(property);
                if (StringUtils.isBlank(uri.getScheme()) || !SUPPORTED_SCHEMES.contains(uri.getScheme().toUpperCase())) {
                    throw new IllegalArgumentException("System property [" + GCS_ENDPOINT_PROPERTY + "=" + property + "] must have a scheme of 'http' or 'https'");
                }
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("System property [" + GCS_ENDPOINT_PROPERTY + "=" + property + "]  must be a valid URI");
            }
        }
        return uri;
    }

    private static String configureServicePath(String property) {
        if (StringUtils.isNotBlank(property)) {
           return property;
        } else {
            return null;
        }
    }
}
