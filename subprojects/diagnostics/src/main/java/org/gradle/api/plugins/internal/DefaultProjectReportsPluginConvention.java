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

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.WrapUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;

import static org.gradle.api.reflect.TypeOf.typeOf;

@Deprecated
@NonNullApi
public abstract class DefaultProjectReportsPluginConvention extends org.gradle.api.plugins.ProjectReportsPluginConvention implements HasPublicType {
    private String projectReportDirName = "project";
    private final Project project;

    @Inject
    public DefaultProjectReportsPluginConvention(Project project) {
        this.project = project;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(org.gradle.api.plugins.ProjectReportsPluginConvention .class);
    }

    @Override
    public String getProjectReportDirName() {
        logDeprecation();
        return projectReportDirName;
    }

    @Override
    public void setProjectReportDirName(String projectReportDirName) {
        logDeprecation();
        this.projectReportDirName = projectReportDirName;
    }

    @Override
    public File getProjectReportDir() {
        logDeprecation();
        return project.getExtensions().getByType(ReportingExtension.class).file(projectReportDirName);
    }

    @Override
    public Set<Project> getProjects() {
        logDeprecation();
        return WrapUtil.toSet(project);
    }

    private static void logDeprecation() {
        DeprecationLogger.deprecateType(org.gradle.api.plugins.ProjectReportsPluginConvention.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "project_report_convention_deprecation")
            .nagUser();
    }
}
