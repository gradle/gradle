/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.maven.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import jarjar.org.apache.maven.execution.*;
import jarjar.org.apache.maven.model.building.ModelBuildingRequest;
import jarjar.org.apache.maven.project.*;
import jarjar.org.apache.maven.settings.Settings;
import jarjar.org.codehaus.plexus.ContainerConfiguration;
import jarjar.org.codehaus.plexus.DefaultContainerConfiguration;
import jarjar.org.codehaus.plexus.DefaultPlexusContainer;
import jarjar.org.codehaus.plexus.PlexusContainerException;
import jarjar.org.codehaus.plexus.classworlds.ClassWorld;
import jarjar.org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import jarjar.org.codehaus.plexus.configuration.PlexusConfigurationException;
import jarjar.org.sonatype.aether.RepositorySystemSession;
import jarjar.org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.gradle.api.GradleException;

import java.io.File;

import static com.google.common.collect.Iterables.transform;

/**
 * by Szczepan Faber, created at: 9/11/12
 */
public class MavenProjectCreator {

    private final Settings mavenSettings;
    private final File pomFile;

    public MavenProjectCreator(Settings mavenSettings, File pomFile) {
        this.mavenSettings = mavenSettings;
        this.pomFile = pomFile;
    }

    public MavenProject create() {
        try {
            return createNow(mavenSettings);
        } catch (Exception e) {
            throw new GradleException("Unable to create MavenProject model.", e);
        }
    }

    private MavenProject createNow(Settings settings) throws PlexusContainerException, PlexusConfigurationException, ComponentLookupException, MavenExecutionRequestPopulationException, ProjectBuildingException {
        ContainerConfiguration containerConfiguration = new DefaultContainerConfiguration()
                .setClassWorld(new ClassWorld("plexus.core", ClassWorld.class.getClassLoader()))
                .setName("mavenCore");

        DefaultPlexusContainer container = new DefaultPlexusContainer(containerConfiguration);
        ProjectBuilder builder = container.lookup(ProjectBuilder.class);
        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        MavenExecutionRequestPopulator populator = container.lookup(MavenExecutionRequestPopulator.class);
        populator.populateFromSettings(executionRequest, settings);
        populator.populateDefaults(executionRequest);
        ProjectBuildingRequest buildingRequest = executionRequest.getProjectBuildingRequest();
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        MavenProject mavenProject = builder.build(pomFile, buildingRequest).getProject();
        Iterable<MavenProject>reactorProjects = transform(builder.build(ImmutableList.of(pomFile), true, buildingRequest), new Function<ProjectBuildingResult, MavenProject>() {
            public MavenProject apply(ProjectBuildingResult from) {
                return from.getProject();
            }
        });
        MavenExecutionResult result = new DefaultMavenExecutionResult();
        result.setProject(mavenProject);
        RepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        MavenSession session = new MavenSession(container, repoSession, executionRequest, result);
        session.setCurrentProject(mavenProject);

        return mavenProject;
    }
}
