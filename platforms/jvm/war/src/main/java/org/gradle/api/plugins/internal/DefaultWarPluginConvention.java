/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.reflect.TypeOf.typeOf;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public abstract class DefaultWarPluginConvention extends org.gradle.api.plugins.WarPluginConvention implements HasPublicType {
    private String webAppDirName;
    private final Project project;

    @Inject
    public DefaultWarPluginConvention(Project project) {
        this.project = project;
        webAppDirName = "src/main/webapp";
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(org.gradle.api.plugins.WarPluginConvention.class);
    }

    @Override
    public File getWebAppDir() {
        logDeprecation();
        return project.file(webAppDirName);
    }

    @Override
    public String getWebAppDirName() {
        logDeprecation();
        return webAppDirName;
    }

    @Override
    public void setWebAppDirName(String webAppDirName) {
        logDeprecation();
        this.webAppDirName = webAppDirName;
    }

    @Override
    public Project getProject() {
        logDeprecation();
        return project;
    }

    private static void logDeprecation() {
        DeprecationLogger.deprecateType(org.gradle.api.plugins.WarPluginConvention.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "war_convention_deprecation")
            .nagUser();
    }
}
