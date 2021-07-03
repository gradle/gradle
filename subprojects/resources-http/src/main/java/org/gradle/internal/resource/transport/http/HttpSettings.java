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
import org.gradle.internal.verifier.HttpRedirectVerifier;

import javax.net.ssl.HostnameVerifier;
import java.util.Collection;

public interface HttpSettings {
    HttpProxySettings getProxySettings();

    HttpProxySettings getSecureProxySettings();

    HttpTimeoutSettings getTimeoutSettings();

    int getMaxRedirects();

    HttpRedirectVerifier getRedirectVerifier();

    RedirectMethodHandlingStrategy getRedirectMethodHandlingStrategy();

    Collection<Authentication> getAuthenticationSettings();

    SslContextFactory getSslContextFactory();

    HostnameVerifier getHostnameVerifier();

    enum RedirectMethodHandlingStrategy {

        /**
         * Follows 307/308 redirects with original method.
         *
         * Mutating requests redirected with 301/302/303 are followed with a GET request.
         */
        ALLOW_FOLLOW_FOR_MUTATIONS,

        /**
         * Always redirects with the original method regardless of type of redirect.
         *
         * @see AlwaysFollowAndPreserveMethodRedirectStrategy for discussion of why this exists (and is default)
         */
        ALWAYS_FOLLOW_AND_PRESERVE
    }
}
