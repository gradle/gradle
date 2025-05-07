/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.Project;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;

@NullMarked
public abstract class DefaultEclipseWtpComponent extends EclipseWtpComponent implements EclipseWtpComponentInternal{
    @Inject
    public DefaultEclipseWtpComponent(Project project, XmlFileContentMerger file) {
        super(project, file);
    }

    @Override
    public Set<File> getSourceDirs() {
        return getSourceDirsProperty().get();
    }

    @Override
    public void setSourceDirs(Set<File> sourceDirs) {
        getSourceDirsProperty().set(sourceDirs);
    }

    @Override
    public String getDeployName() {
        return getDeployNameProperty().get();
    }

    @Override
    public void setDeployName(String deployName) {
        getDeployNameProperty().set(deployName);
    }

    @Override
    public String getContextPath() {
        return getContextPathProperty().getOrNull();
    }

    @Override
    public void setContextPath(String contextPath) {
        this.getContextPathProperty().set(contextPath);
    }

    @Override
    public String getLibDeployPath() {
        return getLibDeployPathProperty().getOrNull();
    }

    @Override
    public void setLibDeployPath(String libDeployPath) {
        this.getLibDeployPathProperty().set(libDeployPath);
    }

    @Override
    public List<WbResource> getResources() {
        return getResourcesProperty().get();
    }

    @Override
    public void setResources(List<WbResource> resources) {
        this.getResourcesProperty().set(resources);
    }
}
