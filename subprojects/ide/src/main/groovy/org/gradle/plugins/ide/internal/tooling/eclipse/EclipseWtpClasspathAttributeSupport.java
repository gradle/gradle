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

package org.gradle.plugins.ide.internal.tooling.eclipse;

import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.eclipse.model.*;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;

import java.util.*;

public class EclipseWtpClasspathAttributeSupport {

    private static final String ATTRIBUTE_WTP_DEPLOYED = "org.eclipse.jst.component.dependency";
    private static final String ATTRIBUTE_WTP_NONDEPLOYED = "org.eclipse.jst.component.nondependency";

    private final boolean isWtpUtilityProject;
    private final Set<ModuleVersionIdentifier> rootConfigModuleVersions;
    private final Set<ModuleVersionIdentifier> libConfigModuleVersions;

    public EclipseWtpClasspathAttributeSupport(Project project, EclipseWtp wtp) {
        this.isWtpUtilityProject = !project.getPlugins().hasPlugin(WarPlugin.class) && !project.getPlugins().hasPlugin(EarPlugin.class);
        this.rootConfigModuleVersions = collectConfigurationsModuleVersions(wtp.getComponent().getRootConfigurations());
        this.libConfigModuleVersions = collectConfigurationsModuleVersions(wtp.getComponent().getLibConfigurations());
    }

    private Set<ModuleVersionIdentifier> collectConfigurationsModuleVersions(Set<Configuration> configurations) {
        Set<ModuleVersionIdentifier> result = new HashSet<ModuleVersionIdentifier>();
        for (Configuration configuration : configurations) {
            ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
            Set<ResolvedArtifact> artifacts = resolvedConfiguration.getResolvedArtifacts();
            for (ResolvedArtifact artifact : artifacts) {
                ResolvedModuleVersion artifactModuleVersion = artifact.getModuleVersion();
                result.add(artifactModuleVersion.getId());
            }
        }
        return result;
    }

    public void defineAttributesForProjectDependencies(Map<ProjectDependency, DefaultEclipseProjectDependency> entryToProjectDependency) {
        for (DefaultEclipseProjectDependency dependency : entryToProjectDependency.values()) {
            dependency.getClasspathAttributes().add(new DefaultClasspathAttribute(ATTRIBUTE_WTP_NONDEPLOYED, ""));
        }
    }

    public void defineAttributesForExternalDependencies(Map<AbstractLibrary, DefaultEclipseExternalDependency> entryToExternalDependency) {
        for (AbstractLibrary library : entryToExternalDependency.keySet()) {
            DefaultEclipseExternalDependency dependency = entryToExternalDependency.get(library);
            defineAttributesForExternalDependency(library, dependency);
        }
    }

    private void defineAttributesForExternalDependency(AbstractLibrary library, DefaultEclipseExternalDependency dependency) {
        DefaultClasspathAttribute classpathAttribute = createDeploymentAttributeFor(library);
        dependency.getClasspathAttributes().add(classpathAttribute);
    }

    private DefaultClasspathAttribute createDeploymentAttributeFor(AbstractLibrary library) {
        if (!isWtpUtilityProject) {
            ModuleVersionIdentifier moduleVersion = library.getModuleVersion();
            if (moduleVersion != null) {
                if (rootConfigModuleVersions.contains(moduleVersion)) {
                    return new DefaultClasspathAttribute(ATTRIBUTE_WTP_DEPLOYED, "/");
                } else if (libConfigModuleVersions.contains(moduleVersion)) {
                    return new DefaultClasspathAttribute(ATTRIBUTE_WTP_DEPLOYED, "/WEB-INF/lib");
                }
            }
        }
        return new DefaultClasspathAttribute(ATTRIBUTE_WTP_NONDEPLOYED, "");
    }

}
