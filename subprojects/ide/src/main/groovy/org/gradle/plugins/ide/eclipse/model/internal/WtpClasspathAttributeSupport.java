/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseWtp;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class WtpClasspathAttributeSupport {
    private final String libDirName;
    private final boolean isUtilityProject;
    private final Set<File> rootConfigFiles;
    private final Set<File> libConfigFiles;

    public WtpClasspathAttributeSupport(Project project, EclipseModel model) {
        isUtilityProject = !project.getPlugins().hasPlugin(WarPlugin.class) && !project.getPlugins().hasPlugin(EarPlugin.class);
        EclipseWtp eclipseWtp = model.getWtp();
        EclipseWtpComponent wtpComponent = eclipseWtp.getComponent();
        libDirName = wtpComponent.getLibDeployPath();
        Set<Configuration> rootConfigs = wtpComponent.getRootConfigurations();
        Set<Configuration> libConfigs = wtpComponent.getLibConfigurations();
        Set<Configuration> minusConfigs = wtpComponent.getMinusConfigurations();
        rootConfigFiles = collectFilesFromConfigs(rootConfigs, minusConfigs);
        libConfigFiles = collectFilesFromConfigs(libConfigs, minusConfigs);
    }

    private static Set<File> collectFilesFromConfigs(Set<Configuration> configs, Set<Configuration> minusConfigs) {
        Set<File> resultFiles = Sets.newHashSet();
        IdeDependenciesExtractor extractor = new IdeDependenciesExtractor();

        Collection<IdeExtendedRepoFileDependency> dependencies = extractor.resolvedExternalDependencies(configs, minusConfigs);
        for (IdeExtendedRepoFileDependency dependency : dependencies) {
            resultFiles.add(dependency.getFile());
        }

        Collection<IdeLocalFileDependency> localDependencies = extractor.extractLocalFileDependencies(configs, minusConfigs);
        for (IdeLocalFileDependency dependency : localDependencies) {
            resultFiles.add(dependency.getFile());
        }

        return resultFiles;
    }

    public void enhance(Classpath classpath) {
        for (ClasspathEntry entry : classpath.getEntries()) {
            if (entry instanceof AbstractClasspathEntry) {
                AbstractClasspathEntry classpathEntry = (AbstractClasspathEntry) entry;
                Map<String, Object> wtpEntries = createDeploymentAttribute(classpathEntry);
                classpathEntry.getEntryAttributes().putAll(wtpEntries);
            }
        }
    }

    private Map<String, Object> createDeploymentAttribute(ClasspathEntry entry) {
        if (entry instanceof AbstractLibrary) {
            return createDeploymentAttribute((AbstractLibrary) entry);
        } else if (entry instanceof ProjectDependency) {
            return createDeploymentAttribute((ProjectDependency)entry);
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> createDeploymentAttribute(AbstractLibrary entry) {
        File file = entry.getLibrary().getFile();
        if (!isUtilityProject) {
            if (rootConfigFiles.contains(file)) {
                return singleEntryMap(AbstractClasspathEntry.COMPONENT_DEPENDENCY_ATTRIBUTE, "/");
            } else if (libConfigFiles.contains(file)) {
                return singleEntryMap(AbstractClasspathEntry.COMPONENT_DEPENDENCY_ATTRIBUTE, libDirName);
            }
        }
        return singleEntryMap(AbstractClasspathEntry.COMPONENT_NON_DEPENDENCY_ATTRIBUTE, "");
    }

    private Map<String, Object> createDeploymentAttribute(ProjectDependency entry) {
        return singleEntryMap(AbstractClasspathEntry.COMPONENT_NON_DEPENDENCY_ATTRIBUTE, "");
    }

    private static Map<String, Object> singleEntryMap(String key, String value) {
        return ImmutableMap.<String, Object>of(key, value);
    }
}
