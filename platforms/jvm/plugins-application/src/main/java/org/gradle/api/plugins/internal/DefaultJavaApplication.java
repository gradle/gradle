/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins.internal;

import org.gradle.api.file.CopySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.deprecation.DeprecationLogger;

public abstract class DefaultJavaApplication implements JavaApplication {
    @SuppressWarnings("deprecation")
    private final org.gradle.api.plugins.ApplicationPluginConvention convention;
    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private final Property<String> executableDir;

    public DefaultJavaApplication(@SuppressWarnings("deprecation") org.gradle.api.plugins.ApplicationPluginConvention convention, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        this.convention = convention;
        this.mainModule = objectFactory.property(String.class);
        this.mainClass = objectFactory.property(String.class).convention(providerFactory.provider(() -> DeprecationLogger.whileDisabled(convention::getMainClassName)));
        this.executableDir = objectFactory.property(String.class).convention(providerFactory.provider(() -> DeprecationLogger.whileDisabled(convention::getExecutableDir)));
    }

    @Override
    public String getApplicationName() {
        return DeprecationLogger.whileDisabled(convention::getApplicationName);
    }

    @Override
    public void setApplicationName(String applicationName) {
        DeprecationLogger.whileDisabled(() -> convention.setApplicationName(applicationName));
    }

    @Override
    public Property<String> getMainModule() {
        return mainModule;
    }

    @Override
    public Property<String> getMainClass() {
        return mainClass;
    }

    @Override
    public Iterable<String> getApplicationDefaultJvmArgs() {
        return DeprecationLogger.whileDisabled(convention::getApplicationDefaultJvmArgs);
    }

    @Override
    public void setApplicationDefaultJvmArgs(Iterable<String> applicationDefaultJvmArgs) {
        DeprecationLogger.whileDisabled(() -> convention.setApplicationDefaultJvmArgs(applicationDefaultJvmArgs));
    }

    @Override
    public Property<String> getExecutableDir() {
        return executableDir;
    }

    @Override
    public CopySpec getApplicationDistribution() {
        return DeprecationLogger.whileDisabled(convention::getApplicationDistribution);
    }

    @Override
    public void setApplicationDistribution(CopySpec applicationDistribution) {
        DeprecationLogger.whileDisabled(() -> convention.setApplicationDistribution(applicationDistribution));
    }
}
