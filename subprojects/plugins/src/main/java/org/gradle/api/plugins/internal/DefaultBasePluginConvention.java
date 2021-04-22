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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

@Deprecated
public class DefaultBasePluginConvention extends BasePluginConvention implements HasPublicType {

    private BasePluginExtension extension;
    private Project project;

    public DefaultBasePluginConvention(Project project, BasePluginExtension extension) {
        this.project = project;
        this.extension = extension;
    }
    @Override
    public TypeOf<?> getPublicType() {
        return TypeOf.typeOf(BasePluginConvention.class);
    }

    @Override
    public DirectoryProperty getDistsDirectory() {
        return extension.getDistsDirectory();
    }

    @Override
    public DirectoryProperty getLibsDirectory() {
        return extension.getLibsDirectory();
    }

    @Override
    public String getDistsDirName() {
        return extension.getDistsDirectory().get().getAsFile().getName();
    }

    @Override
    public void setDistsDirName(String distsDirName) {
        extension.getDistsDirectory().set(project.getLayout().getBuildDirectory().dir(distsDirName));
    }

    @Override
    public String getLibsDirName() {
        return extension.getLibsDirectory().get().getAsFile().getName();
    }

    @Override
    public void setLibsDirName(String libsDirName) {
        extension.getLibsDirectory().set(project.getLayout().getBuildDirectory().dir(libsDirName));
    }

    @Override
    public String getArchivesBaseName() {
        return extension.getArchivesBaseName().get();
    }

    @Override
    public void setArchivesBaseName(String archivesBaseName) {
        extension.getArchivesBaseName().set(archivesBaseName);
    }
}
