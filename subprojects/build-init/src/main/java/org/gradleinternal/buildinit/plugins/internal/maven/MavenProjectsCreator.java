/*
 * Copyright 2021 the original author or authors.
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

package org.gradleinternal.buildinit.plugins.internal.maven;

import com.google.common.collect.ImmutableList;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.internal.SystemProperties;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class MavenProjectsCreator {

    public Set<MavenProject> create(Settings mavenSettings, File pomFile) {
        if (!pomFile.exists()) {
            throw new MavenConversionException(String.format("Unable to create Maven project model. The POM file %s does not exist.", pomFile));
        }
        try {
            return createNow(mavenSettings, pomFile);
        } catch (Exception e) {
            throw new MavenConversionException(String.format("Unable to create Maven project model using POM %s.", pomFile), e);
        }
    }

    private Set<MavenProject> createNow(Settings settings, File pomFile) throws PlexusContainerException, ComponentLookupException, MavenExecutionRequestPopulationException, ProjectBuildingException {
        ContainerConfiguration containerConfiguration = new DefaultContainerConfiguration()
                .setClassWorld(new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader()))
                .setName("mavenCore").setClassPathScanning(PlexusConstants.SCANNING_INDEX).setAutoWiring(true);

        DefaultPlexusContainer container = new DefaultPlexusContainer(containerConfiguration);
        ProjectBuilder builder = container.lookup(ProjectBuilder.class);
        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        final Properties properties = SystemProperties.getInstance().withSystemProperties(() -> {
            final Properties currentProperties = new Properties();
            currentProperties.putAll(System.getProperties());
            return currentProperties;
        });

        executionRequest.setSystemProperties(properties);
        MavenExecutionRequestPopulator populator = container.lookup(MavenExecutionRequestPopulator.class);
        populateFromSettings(settings, executionRequest, populator);
        populator.populateDefaults(executionRequest);
        ProjectBuildingRequest buildingRequest = executionRequest.getProjectBuildingRequest();
        buildingRequest.getRemoteRepositories().forEach(repository -> {
            if (repository.getId().equals(RepositorySystem.DEFAULT_REMOTE_REPO_ID)) {
                repository.setUrl(RepositoryHandler.MAVEN_CENTRAL_URL);
            }
        });
        buildingRequest.setProcessPlugins(false);
        MavenProject mavenProject = builder.build(pomFile, buildingRequest).getProject();
        Set<MavenProject> reactorProjects = new LinkedHashSet<>();

        //TODO adding the parent project first because the converter needs it this way ATM. This is oversimplified.
        //the converter should not depend on the order of reactor projects.
        //we should add coverage for nested multi-project builds with multiple parents.
        reactorProjects.add(mavenProject);
        List<ProjectBuildingResult> allProjects = builder.build(ImmutableList.of(pomFile), true, buildingRequest);
        //noinspection NullableProblems
        CollectionUtils.collect(allProjects, reactorProjects, ProjectBuildingResult::getProject);

        MavenExecutionResult result = new DefaultMavenExecutionResult();
        result.setProject(mavenProject);
        RepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        @SuppressWarnings("deprecation")
        MavenSession session = new MavenSession(container, repoSession, executionRequest, result);
        session.setCurrentProject(mavenProject);

        return reactorProjects;
    }

    @SuppressWarnings("deprecation")
    private void populateFromSettings(Settings settings, MavenExecutionRequest executionRequest, MavenExecutionRequestPopulator populator) throws MavenExecutionRequestPopulationException {
        populator.populateFromSettings(executionRequest, settings);
    }
}
