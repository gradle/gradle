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
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.reflect.TypeOf.typeOf;

public abstract class DefaultWarPluginConvention extends WarPluginConvention implements HasPublicType {
    private String webAppDirName;
    private final Project project;

    @Inject
    public DefaultWarPluginConvention(Project project) {
        this.project = project;
        webAppDirName = "src/main/webapp";
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(WarPluginConvention.class);
    }

    @Override
    public File getWebAppDir() {
        return project.file(webAppDirName);
    }

    @Override
    public String getWebAppDirName() {
        return webAppDirName;
    }

    @Override
    public void setWebAppDirName(String webAppDirName) {
        this.webAppDirName = webAppDirName;
    }

    @Override
    public Project getProject() {
        return project;
    }
}
