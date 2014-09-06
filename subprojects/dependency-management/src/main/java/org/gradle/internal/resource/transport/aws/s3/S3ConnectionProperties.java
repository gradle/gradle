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

import org.gradle.internal.resource.transport.http.HttpProxySettings;
import org.gradle.internal.resource.transport.http.JavaSystemPropertiesSecureHttpProxySettings;

import static java.lang.System.getProperty;

public class S3ConnectionProperties {
    public static final String S3_ENDPOINT_PROPERTY = "org.gradle.s3.endpoint";
    private final String endpoint;
    private final HttpProxySettings proxySettings;

    public S3ConnectionProperties() {
        endpoint = getProperty(S3_ENDPOINT_PROPERTY);
        proxySettings = new JavaSystemPropertiesSecureHttpProxySettings();
    }

    public String getEndpoint() {
        return endpoint;
    }

    public HttpProxySettings getProxySettings() {
        return proxySettings;
    }
}
