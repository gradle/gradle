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
import org.gradle.tooling.internal.protocol.BuildVersion1;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.GradleConnectionVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseBuildVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion1;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultGradleConnection implements GradleConnectionVersion1 {
    private final File projectDir;

    public DefaultGradleConnection(File projectDir) {
        this.projectDir = projectDir;
    }

    public <T extends BuildVersion1> T getModel(Class<T> type) throws UnsupportedOperationException {
        if (type.equals(EclipseBuildVersion1.class)) {
            StartParameter startParameter = new StartParameter();
            startParameter.setProjectDir(projectDir);
            startParameter.setSearchUpwards(false);

            final GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
            final ModelBuilder builder = new ModelBuilder();
            gradleLauncher.addListener(builder);
            gradleLauncher.getBuildAnalysis().rethrowFailure();
            return type.cast(new EclipseBuildImpl(builder.rootProject));
        }

        throw new UnsupportedOperationException();
    }

    private static class ModelBuilder extends BuildAdapter {
        private EclipseProjectImpl rootProject;

        @Override
        public void projectsEvaluated(Gradle gradle) {
            rootProject = build(gradle.getRootProject());
        }

        private EclipseProjectImpl build(Project project) {
            Configuration configuration = project.getConfigurations().findByName(
                    "testRuntime");
            List<ExternalDependencyVersion1> dependencies = new ArrayList<ExternalDependencyVersion1>();
            if (configuration != null) {
                Set<File> classpath = configuration.getFiles();
                for (final File file : classpath) {
                    dependencies.add(new ExternalDependencyVersion1() {
                        public File getFile() {
                            return file;
                        }
                    });
                }
            }
            List<EclipseProjectVersion1> children = new ArrayList<EclipseProjectVersion1>();
            for (Project child : project.getChildProjects().values()) {
                children.add(build(child));
            }

            return new EclipseProjectImpl(project.getName(), children, dependencies);
        }
    }

    private static class EclipseProjectImpl implements EclipseProjectVersion1 {
        private final String name;
        private final List<ExternalDependencyVersion1> classpath;
        private final List<EclipseProjectVersion1> children;

        public EclipseProjectImpl(String name, Iterable<? extends EclipseProjectVersion1> children, Iterable<ExternalDependencyVersion1> classpath) {
            this.name = name;
            this.children = GUtil.addLists(children);
            this.classpath = GUtil.addLists(classpath);
        }

        public String getName() {
            return name;
        }

        public List<EclipseProjectVersion1> getChildProjects() {
            return children;
        }

        public List<ExternalDependencyVersion1> getClasspath() {
            return classpath;
        }
    }

    private static class EclipseBuildImpl implements EclipseBuildVersion1 {
        private final EclipseProjectVersion1 rootProject;

        public EclipseBuildImpl(EclipseProjectVersion1 rootProject) {
            this.rootProject = rootProject;
        }

        public EclipseProjectVersion1 getRootProject() {
            return rootProject;
        }
    }
}
