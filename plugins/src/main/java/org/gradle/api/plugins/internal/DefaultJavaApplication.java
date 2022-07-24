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
import org.gradle.api.plugins.ApplicationPluginConvention;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.deprecation.DeprecationLogger;

public class DefaultJavaApplication implements JavaApplication {
    private final ApplicationPluginConvention convention;
    private final Property<String> mainModule;
    private final Property<String> mainClass;

    public DefaultJavaApplication(ApplicationPluginConvention convention, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        this.convention = convention;
        this.mainModule = objectFactory.property(String.class);
        this.mainClass = objectFactory.property(String.class).convention(providerFactory.provider(convention::getMainClassName));
    }

    @Override
    public String getApplicationName() {
        return convention.getApplicationName();
    }

    @Override
    public void setApplicationName(String applicationName) {
        convention.setApplicationName(applicationName);
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
    public String getMainClassName() {
        DeprecationLogger.deprecateMethod(JavaApplication.class, "getMainClassName()")
            .withAdvice("Use #getMainClass() instead.")
            .willBeRemovedInGradle8()
            .withDslReference(JavaApplication.class, "mainClass")
            .nagUser();
        return mainClass.getOrNull();
    }

    @Override
    public void setMainClassName(String mainClassName) {
        DeprecationLogger.deprecateMethod(JavaApplication.class, "setMainClassName(String)")
            .withAdvice("Use #getMainClass().set(...) instead.")
            .willBeRemovedInGradle8()
            .withDslReference(JavaApplication.class, "mainClass")
            .nagUser();
        mainClass.set(mainClassName);
        convention.setMainClassName(mainClassName);
    }

    @Override
    public Iterable<String> getApplicationDefaultJvmArgs() {
        return convention.getApplicationDefaultJvmArgs();
    }

    @Override
    public void setApplicationDefaultJvmArgs(Iterable<String> applicationDefaultJvmArgs) {
        convention.setApplicationDefaultJvmArgs(applicationDefaultJvmArgs);
    }

    @Override
    public String getExecutableDir() {
        return convention.getExecutableDir();
    }

    @Override
    public void setExecutableDir(String executableDir) {
        convention.setExecutableDir(executableDir);
    }

    @Override
    public CopySpec getApplicationDistribution() {
        return convention.getApplicationDistribution();
    }

    @Override
    public void setApplicationDistribution(CopySpec applicationDistribution) {
        convention.setApplicationDistribution(applicationDistribution);
    }
}
