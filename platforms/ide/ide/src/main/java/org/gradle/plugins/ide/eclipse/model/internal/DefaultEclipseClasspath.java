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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.provider.views.ListPropertyListView;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;

public abstract class DefaultEclipseClasspath extends EclipseClasspath implements EclipseClasspathInternal {
    @Inject
    public DefaultEclipseClasspath(Project project) {
        super(project);
    }

    @Override
    public Collection<Configuration> getPlusConfigurations() {
        return new ListPropertyListView<>(getPlusConfigurationsProperty());
    }

    @Override
    public void setPlusConfigurations(Collection<Configuration> plusConfigurations) {
        this.getPlusConfigurationsProperty().set(plusConfigurations);
    }

    @Override
    public File getDefaultOutputDir() {
        return getDefaultOutputDirProperty().getAsFile().get();
    }

    @Override
    public void setDefaultOutputDir(File defaultOutputDir) {
        this.getDefaultOutputDirProperty().fileValue(defaultOutputDir);
    }
}
