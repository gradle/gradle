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

package org.gradle.api.plugins.internal;

import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.inject.Inject;
import java.util.ArrayList;

import static org.gradle.api.reflect.TypeOf.typeOf;

@SuppressWarnings("deprecation")
public abstract class DefaultApplicationPluginConvention extends org.gradle.api.plugins.ApplicationPluginConvention implements HasPublicType {
    private String applicationName;
    private String mainClassName;
    private Iterable<String> applicationDefaultJvmArgs = new ArrayList<String>();
    private String executableDir = "bin";
    private CopySpec applicationDistribution;

    private final Project project;

    @Inject
    public DefaultApplicationPluginConvention(Project project) {
        this.project = project;
        applicationDistribution = project.copySpec();
    }

    @Override
    @SuppressWarnings("deprecation")
    public TypeOf<?> getPublicType() {
        return typeOf(org.gradle.api.plugins.ApplicationPluginConvention.class);
    }

    @Override
    public String getApplicationName() {
        logDeprecation();
        return applicationName;
    }

    @Override
    public void setApplicationName(String applicationName) {
        logDeprecation();
        this.applicationName = applicationName;
    }

    @Override
    public String getMainClassName() {
        logDeprecation();
        return mainClassName;
    }

    @Override
    public void setMainClassName(String mainClassName) {
        logDeprecation();
        this.mainClassName = mainClassName;
    }

    @Override
    public Iterable<String> getApplicationDefaultJvmArgs() {
        logDeprecation();
        return applicationDefaultJvmArgs;
    }

    @Override
    public void setApplicationDefaultJvmArgs(Iterable<String> applicationDefaultJvmArgs) {
        logDeprecation();
        this.applicationDefaultJvmArgs = applicationDefaultJvmArgs;
    }

    @Override
    public String getExecutableDir() {
        logDeprecation();
        return executableDir;
    }

    @Override
    public void setExecutableDir(String executableDir) {
        logDeprecation();
        this.executableDir = executableDir;
    }

    @Override
    public CopySpec getApplicationDistribution() {
        logDeprecation();
        return applicationDistribution;
    }

    @Override
    public void setApplicationDistribution(CopySpec applicationDistribution) {
        logDeprecation();
        this.applicationDistribution = applicationDistribution;
    }

    @Override
    public Project getProject() {
        logDeprecation();
        return project;
    }

    @SuppressWarnings("deprecation")
    private static void logDeprecation() {
        DeprecationLogger.deprecateType(org.gradle.api.plugins.ApplicationPluginConvention.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "application_convention_deprecation")
            .nagUser();
    }
}
