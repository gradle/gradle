/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.testfixtures;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.invocation.DefaultGradle;
import org.gradle.testfixtures.internal.GlobalTestServices;
import org.gradle.testfixtures.internal.TestTopLevelBuildServiceRegistry;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;

/**
 * <p>Creates dummy instances of {@link org.gradle.api.Project} which you can use in testing custom task and plugin
 * implementations.</p>
 *
 * <p>To create a project instance:</p>
 *
 * <ol>
 *
 * <li>Create a {@code ProjectBuilder} instance by calling {@link #builder()}.</li>
 *
 * <li>Optionally, configure the builder.</li>
 *
 * <li>Call {@link #build()} to create the {@code Project} instance.</li>
 *
 * </ol>
 *
 * <p>You can reuse a builder to create multiple {@code Project} instances.</p>
 */
public class ProjectBuilder {
    private static final GlobalTestServices GLOBAL_SERVICES = new GlobalTestServices();
    private File projectDir;

    /**
     * Creates a project builder.
     *
     * @return The builder
     */
    public static ProjectBuilder builder() {
        return new ProjectBuilder();
    }

    /**
     * Specifies the project directory for the project to build.
     *
     * @param dir The project directory
     * @return A new ProjectBuilder.
     */
    public ProjectBuilder withProjectDir(File dir) {
        projectDir = GFileUtils.canonicalise(dir);
        return this;
    }

    /**
     * Creates the project.
     *
     * @return The project
     */
    public Project build() {
        if (projectDir == null) {
            try {
                projectDir = GFileUtils.canonicalise(File.createTempFile("gradle", "projectDir"));
                projectDir.delete();
                projectDir.mkdir();
                projectDir.deleteOnExit();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        final File homeDir = new File(projectDir, "gradleHome");

        StartParameter startParameter = new StartParameter();
        startParameter.setGradleUserHomeDir(new File(projectDir, "userHome"));

        ServiceRegistryFactory topLevelRegistry = new TestTopLevelBuildServiceRegistry(GLOBAL_SERVICES, startParameter, homeDir);
        GradleInternal gradle = new DefaultGradle(null, startParameter, topLevelRegistry);

        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(null, "test", projectDir, new DefaultProjectDescriptorRegistry());
        ProjectInternal project = topLevelRegistry.get(IProjectFactory.class).createProject(projectDescriptor, null, gradle);

        gradle.setRootProject(project);
        gradle.setDefaultProject(project);

        return project;
    }

}
