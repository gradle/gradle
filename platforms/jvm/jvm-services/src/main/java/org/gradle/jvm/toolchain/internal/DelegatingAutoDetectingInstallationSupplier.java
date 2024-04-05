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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.Collections;
import java.util.Set;

public class DelegatingAutoDetectingInstallationSupplier implements InstallationSupplier {
    private final Provider<Boolean> enabled;
    private final InstallationSupplier delegate;

    public DelegatingAutoDetectingInstallationSupplier(ProviderFactory providerFactory, InstallationSupplier delegate) {
        this.enabled = providerFactory.gradleProperty(AutoDetectingInstallationSupplier.AUTO_DETECT).map(Boolean::parseBoolean).orElse(Boolean.TRUE);
        this.delegate = delegate;
    }

    @Override
    public String getSourceName() {
        return delegate.getSourceName();
    }

    @Override
    public Set<InstallationLocation> get() {
        if (enabled.get()) {
            return delegate.get();
        }
        return Collections.emptySet();
    }
}
