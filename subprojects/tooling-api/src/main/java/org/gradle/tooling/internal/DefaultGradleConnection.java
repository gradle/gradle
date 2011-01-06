/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal;

import org.gradle.BuildAdapter;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.tooling.GradleConnection;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.Build;
import org.gradle.tooling.model.Dependency;
import org.gradle.tooling.model.ExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseBuild;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultGradleConnection implements GradleConnection {
    private final File projectDir;

    public DefaultGradleConnection(File projectDir) {
        this.projectDir = projectDir;
    }

    public <T extends Build> T getModel(Class<T> viewType) throws UnsupportedVersionException {
        if (viewType.equals(EclipseBuild.class)) {
            StartParameter startParameter = new StartParameter();
            startParameter.setProjectDir(projectDir);
            startParameter.setSearchUpwards(false);

            final GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
            final ModelBuilder builder = new ModelBuilder();
            gradleLauncher.addListener(builder);
            gradleLauncher.getBuildAnalysis().rethrowFailure();
            return viewType.cast(new EclipseBuildImpl(builder.rootProject));
        }

        throw new UnsupportedVersionException();
    }

    private static class ModelBuilder extends BuildAdapter {
        private EclipseProjectImpl rootProject;

        @Override
        public void projectsEvaluated(Gradle gradle) {
            rootProject = build(gradle.getRootProject());
        }

        private EclipseProjectImpl build(Project project) {
            Configuration configuration = project.getConfigurations().findByName(
                    JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME);
            List<ExternalDependency> dependencies = new ArrayList<ExternalDependency>();
            if (configuration != null) {
                Set<File> classpath = configuration.getFiles();
                for (final File file : classpath) {
                    dependencies.add(new ExternalDependency() {
                        public File getFile() {
                            return file;
                        }
                    });
                }
            }
            List<EclipseProject> children = new ArrayList<EclipseProject>();
            for (Project child : project.getChildProjects().values()) {
                children.add(build(child));
            }

            return new EclipseProjectImpl(project.getName(), children, dependencies);
        }
    }

    private static class EclipseProjectImpl implements EclipseProject {
        private final String name;
        private final List<? extends Dependency> classpath;
        private final List<EclipseProject> children;

        public EclipseProjectImpl(String name, List<EclipseProject> children, List<? extends Dependency> classpath) {
            this.name = name;
            this.children = children;
            this.classpath = classpath;
        }

        public String getName() {
            return name;
        }

        public List<EclipseProject> getChildProjects() {
            return children;
        }

        public List<? extends Dependency> getClasspath() {
            return classpath;
        }
    }

    private static class EclipseBuildImpl implements EclipseBuild {
        private final EclipseProject rootProject;

        public EclipseBuildImpl(EclipseProject rootProject) {
            this.rootProject = rootProject;
        }

        public EclipseProject getRootProject() {
            return rootProject;
        }
    }
}
