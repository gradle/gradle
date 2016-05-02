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
import org.gradle.plugins.ear.Ear;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.eclipse.model.*;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;

import java.util.*;

public abstract class EclipseWtpClasspathAttributeSupport {

    public abstract void defineAttributesForExternalDependencies(Map<AbstractLibrary, DefaultEclipseExternalDependency> entryToExternalDependency);

    public abstract void defineAttributesForProjectDependencies(Map<ProjectDependency, DefaultEclipseProjectDependency> entryToProjectDependency);

    private static final String ATTRIBUTE_WTP_DEPLOYED = "org.eclipse.jst.component.dependency";
    private static final String ATTRIBUTE_WTP_NONDEPLOYED = "org.eclipse.jst.component.nondependency";
    private static final String DEFAULT_DEPLOY_DIR_NAME = "/WEB-INF/lib";

    public static final EclipseWtpClasspathAttributeSupport from(Project project) {
        EclipseModel eclipseModel = project.getExtensions().findByType(EclipseModel.class);
        if (eclipseModel != null) {
            EclipseWtp eclipseWtp = eclipseModel.getWtp();
            if (eclipseWtp != null) {
                boolean isUtilityProject = !project.getPlugins().hasPlugin(WarPlugin.class) && !project.getPlugins().hasPlugin(EarPlugin.class);

                Ear ear = (Ear) project.getTasks().findByName(EarPlugin.EAR_TASK_NAME);
                String libDirName = ear == null ? DEFAULT_DEPLOY_DIR_NAME : ear.getLibDirName();

                IdeDependenciesExtractor depsExtractor = new IdeDependenciesExtractor();

                EclipseWtpComponent wtpComponent = eclipseWtp.getComponent();
                Set<Configuration> rootConfigs = wtpComponent.getRootConfigurations();
                Set<Configuration> libConfigs = wtpComponent.getLibConfigurations();
                Set<Configuration> minusConfigs = wtpComponent.getMinusConfigurations();

                return new DefaultWtpClasspathAttributeSupport(depsExtractor, isUtilityProject, libDirName, rootConfigs, libConfigs, minusConfigs);
            }
        }

        return new NoOpWtpClasspathAttributeSupport();
    }

    private static class NoOpWtpClasspathAttributeSupport extends EclipseWtpClasspathAttributeSupport {

        @Override
        public void defineAttributesForExternalDependencies(Map<AbstractLibrary, DefaultEclipseExternalDependency> entryToExternalDependency) {
        }

        @Override
        public void defineAttributesForProjectDependencies(Map<ProjectDependency, DefaultEclipseProjectDependency> entryToProjectDependency) {
        }
    }

    private static class DefaultWtpClasspathAttributeSupport extends EclipseWtpClasspathAttributeSupport {

        private final String libDirName;
        private final boolean isUtilityProject;
        private final Set<ModuleVersionIdentifier> rootConfigModuleVersions;
        private final Set<ModuleVersionIdentifier> libConfigModuleVersions;


        public DefaultWtpClasspathAttributeSupport(IdeDependenciesExtractor depsExtractor, boolean isUtilityProject,
                                                   String libDirName, Set<Configuration> rootConfigs,
                                                   Set<Configuration> libConfigs, Set<Configuration> minusConfigs) {
            this.isUtilityProject = isUtilityProject;
            this.libDirName = libDirName;
            this.rootConfigModuleVersions = collectModuleVersions(depsExtractor, rootConfigs, minusConfigs);
            this.libConfigModuleVersions = collectModuleVersions(depsExtractor, libConfigs, minusConfigs);
        }

        private Set<ModuleVersionIdentifier> collectModuleVersions(IdeDependenciesExtractor depsExtractor, Set<Configuration> configs, Set<Configuration> minusConfigs) {
            Collection<IdeExtendedRepoFileDependency> dependencies = depsExtractor.resolvedExternalDependencies(configs, minusConfigs);
            Set<ModuleVersionIdentifier> result = new HashSet<ModuleVersionIdentifier>();
            for (IdeExtendedRepoFileDependency dependency : dependencies) {
                dependency.getId();
                result.add(dependency.getId());
            }
            return result;
        }

        @Override
        public void defineAttributesForProjectDependencies(Map<ProjectDependency, DefaultEclipseProjectDependency> entryToProjectDependency) {
            for (DefaultEclipseProjectDependency dependency : entryToProjectDependency.values()) {
                dependency.getClasspathAttributes().add(new DefaultClasspathAttribute(ATTRIBUTE_WTP_NONDEPLOYED, ""));
            }
        }

        @Override
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
            if (!isUtilityProject) {
                ModuleVersionIdentifier moduleVersion = library.getModuleVersion();
                if (moduleVersion != null) {
                    if (rootConfigModuleVersions.contains(moduleVersion)) {
                        return new DefaultClasspathAttribute(ATTRIBUTE_WTP_DEPLOYED, "/");
                    } else if (libConfigModuleVersions.contains(moduleVersion)) {
                        return new DefaultClasspathAttribute(ATTRIBUTE_WTP_DEPLOYED, libDirName);
                    }
                }
            }
            return new DefaultClasspathAttribute(ATTRIBUTE_WTP_NONDEPLOYED, "");
        }
    }
}
