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
import org.gradle.api.provider.Property;

import java.util.ArrayList;

public class DefaultJavaApplication implements JavaApplication {
    private final Property<String> mainModule;
    private String applicationName;
    private Property<String> mainClass;
    private Iterable<String> applicationDefaultJvmArgs = new ArrayList<String>();
    private String executableDirectory = "bin";
    private CopySpec applicationDistribution;

    public DefaultJavaApplication(ObjectFactory objectFactory, Project project) {
        this.mainModule = objectFactory.property(String.class);
        this.mainClass = objectFactory.property(String.class);
        this.applicationDistribution = project.copySpec();
    }

    @Override
    public String getApplicationName() {
        return applicationName;
    }

    @Override
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
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
        return applicationDefaultJvmArgs;
    }

    @Override
    public void setApplicationDefaultJvmArgs(Iterable<String> applicationDefaultJvmArgs) {
        this.applicationDefaultJvmArgs = applicationDefaultJvmArgs;
    }

    @Override
    public String getExecutableDir() {
        return executableDirectory;
    }

    @Override
    public void setExecutableDir(String executableDir) {
        this.executableDirectory = executableDir;
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
