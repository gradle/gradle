/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.jvm.toolchain.internal.install;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class JavaToolchainHttpRedirectVerifierFactory {

    public HttpRedirectVerifier createVerifier(URI toolchainUri) {
        final HttpRedirectVerifier redirectVerifier;
        try {
            redirectVerifier = HttpRedirectVerifierFactory.create(new URI(toolchainUri.getScheme(), toolchainUri.getAuthority(), null, null, null), false,
                () -> {
                    throw new InvalidUserCodeException("Attempting to download java toolchain from an insecure URI " + toolchainUri + ". This is not supported, use a secure URI instead.");
                },
                uri -> {
                    throw new InvalidUserCodeException("Attempting to download java toolchain from an insecure URI " + uri +
                        ". This URI was reached as a redirect from " + toolchainUri + ". This is not supported, make sure no insecure URIs appear in the redirect");
                });
        } catch (URISyntaxException e) {
            throw new InvalidUserCodeException("Cannot extract host information from specified URI " + toolchainUri);
        }
        return redirectVerifier;
    }
}
