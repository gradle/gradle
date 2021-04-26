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
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.plugins.WarPluginExtension;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.util.internal.RelativePathUtil;

import java.io.File;

import static org.gradle.api.reflect.TypeOf.typeOf;

public class DefaultWarPluginConvention extends WarPluginConvention implements HasPublicType {

    private final Project project;
    private final WarPluginExtension extension;

    public DefaultWarPluginConvention(Project project, WarPluginExtension extension) {
        this.project = project;
        this.extension = extension;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(WarPluginConvention.class);
    }

    @Override
    public File getWebAppDir() {
        return extension.getWebAppDir().get().getAsFile();
    }

    @Override
    public String getWebAppDirName() {
        File projectDir = project.getProjectDir();
        File webAppDir = extension.getWebAppDir().get().getAsFile();
        return RelativePathUtil.relativePath(projectDir, webAppDir);
    }

    @Override
    public void setWebAppDirName(String webAppDirName) {
        extension.getWebAppDir().set(project.getLayout().getProjectDirectory().dir(webAppDirName));
    }

    @Override
    public Project getProject() {
        return project;
    }
}
