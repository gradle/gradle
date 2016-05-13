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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.plugins.ear.Ear;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.eclipse.EclipseWtpPlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseWtp;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class EclipseWtpSupport {

    private static final String ATTRIBUTE_WTP_DEPLOYED = "org.eclipse.jst.component.dependency";
    private static final String ATTRIBUTE_WTP_NONDEPLOYED = "org.eclipse.jst.component.nondependency";
    private static final String DEFAULT_DEPLOY_DIR_NAME = "/WEB-INF/lib";

    public static void applyEclipseWtpPluginOnWebProjects(Project root) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            if (isWebProject(p)) {
                p.getPluginManager().apply(EclipseWtpPlugin.class);
            }
        }
    }

    private static boolean isWebProject(Project project) {
        PluginContainer container = project.getPlugins();
        return container.hasPlugin(WarPlugin.class)
            || container.hasPlugin(EarPlugin.class)
            || container.hasPlugin(EclipseWtpPlugin.class);
    }

    public static void enhanceProject(Project project, List<DefaultEclipseProjectDependency> projectDependencies, List<DefaultEclipseExternalDependency> externalDependencies) {
        if (isWebProject(project)) {
            EclipseWtp eclipseWtp = project.getExtensions().findByType(EclipseModel.class).getWtp();
            boolean isUtilityProject = !project.getPlugins().hasPlugin(WarPlugin.class) && !project.getPlugins().hasPlugin(EarPlugin.class);

            Ear ear = (Ear) project.getTasks().findByName(EarPlugin.EAR_TASK_NAME);
            String libDirName = ear == null ? DEFAULT_DEPLOY_DIR_NAME : ear.getLibDirName();

            IdeDependenciesExtractor depsExtractor = new IdeDependenciesExtractor();

            EclipseWtpComponent wtpComponent = eclipseWtp.getComponent();
            Set<Configuration> rootConfigs = wtpComponent.getRootConfigurations();
            Set<Configuration> libConfigs = wtpComponent.getLibConfigurations();
            Set<Configuration> minusConfigs = wtpComponent.getMinusConfigurations();

            WtpClasspathAttributeSupport wtpSupport = new WtpClasspathAttributeSupport(depsExtractor, isUtilityProject, libDirName, rootConfigs, libConfigs, minusConfigs);
            wtpSupport.defineAttributesForProjectDependencies(projectDependencies);
            wtpSupport.defineAttributesForExternalDependencies(externalDependencies);
        }
    }

    private static class WtpClasspathAttributeSupport {

        private final String libDirName;
        private final boolean isUtilityProject;
        private final Set<ModuleVersionIdentifier> rootConfigModuleVersions;
        private final Set<ModuleVersionIdentifier> libConfigModuleVersions;


        private WtpClasspathAttributeSupport(IdeDependenciesExtractor depsExtractor, boolean isUtilityProject,
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

        public void defineAttributesForProjectDependencies(List<DefaultEclipseProjectDependency> projectDependencies) {
            for (DefaultEclipseProjectDependency dependency : projectDependencies) {
                dependency.getClasspathAttributes().add(new DefaultClasspathAttribute(ATTRIBUTE_WTP_NONDEPLOYED, ""));
            }
        }

        public void defineAttributesForExternalDependencies(List<DefaultEclipseExternalDependency> externalDependencies) {
            for (DefaultEclipseExternalDependency dependency : externalDependencies) {
                defineAttributesForExternalDependency(dependency);
            }
        }

        private void defineAttributesForExternalDependency(DefaultEclipseExternalDependency dependency) {
            DefaultClasspathAttribute classpathAttribute = createDeploymentAttributeFor(dependency);
            dependency.getClasspathAttributes().add(classpathAttribute);
        }

        private DefaultClasspathAttribute createDeploymentAttributeFor(DefaultEclipseExternalDependency dependency) {
            if (!isUtilityProject) {
                ModuleVersionIdentifier moduleVersion = dependency.getModuleVersionIdentifier();
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
