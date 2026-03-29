/*
 * Copyright 2026 the original author or authors.
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

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DeprecatedSchemeFactory implements AuthSchemeFactory {
    private final AuthSchemeFactory delegate;
    private final String authScheme;

    public DeprecatedSchemeFactory(AuthSchemeFactory delegate, String authScheme) {
        this.delegate = delegate;
        this.authScheme = authScheme;
    }

    @Override
    public AuthScheme create(HttpContext context) {
        DeprecationLogger.deprecateBehaviour("Authenticating to repositories with scheme '" + authScheme + "'.")
            .withAdvice("Consider using Basic or Bearer authentication with TLS instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "auth_schemes")
            .nagUser();
        return delegate.create(context);
    }
}
