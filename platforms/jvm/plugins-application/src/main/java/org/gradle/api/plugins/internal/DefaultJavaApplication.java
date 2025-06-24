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

import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public class DefaultJavaApplication implements JavaApplication {
    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private final Property<String> executableDir;
    private final Property<String> applicationName;
    private final ListProperty<String> applicationDefaultJvmArgs;
    private CopySpec applicationDistribution;

    public DefaultJavaApplication(ObjectFactory objectFactory, Project project) {
        this.mainModule = objectFactory.property(String.class);
        this.mainClass = objectFactory.property(String.class);
        this.executableDir = objectFactory.property(String.class).convention("bin");
        this.applicationName = objectFactory.property(String.class);
        this.applicationDefaultJvmArgs = objectFactory.listProperty(String.class);
        this.applicationDistribution = project.copySpec();
    }

    @Override
    public Property<String> getApplicationName() {
        return applicationName;
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
    public ListProperty<String> getApplicationDefaultJvmArgs() {
        return applicationDefaultJvmArgs;
    }

    @Override
    public Property<String> getExecutableDir() {
        return executableDir;
    }

    @Override
    public CopySpec getApplicationDistribution() {
        return applicationDistribution;
    }

    @Override
    public void setApplicationDistribution(CopySpec applicationDistribution) {
        this.applicationDistribution = applicationDistribution;
    }
}
