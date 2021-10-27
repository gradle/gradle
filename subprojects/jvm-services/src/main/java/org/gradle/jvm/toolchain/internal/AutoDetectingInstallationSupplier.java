/*
 * Copyright 2020 the original author or authors.
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

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

public abstract class AutoDetectingInstallationSupplier implements InstallationSupplier {

    public static final String AUTO_DETECT = "org.gradle.java.installations.auto-detect";
    private final ProviderFactory factory;
    private final Provider<Boolean> detectionEnabled;

    @Inject
    public AutoDetectingInstallationSupplier(ProviderFactory factory) {
        this.detectionEnabled = factory.gradleProperty(AUTO_DETECT).forUseAtConfigurationTime().map(Boolean::parseBoolean);
        this.factory = factory;
    }

    @Override
    public Set<InstallationLocation> get() {
        if (isAutoDetectionEnabled()) {
            return findCandidates();
        }
        return Collections.emptySet();
    }

    protected Provider<String> getEnvironmentProperty(String propertyName) {
        return factory.environmentVariable(propertyName).forUseAtConfigurationTime();
    }

    protected Provider<String> getSystemProperty(String propertyName) {
        return factory.systemProperty(propertyName).forUseAtConfigurationTime();
    }

    protected abstract Set<InstallationLocation> findCandidates();

    protected boolean isAutoDetectionEnabled() {
        return detectionEnabled.getOrElse(true);
    }

}
