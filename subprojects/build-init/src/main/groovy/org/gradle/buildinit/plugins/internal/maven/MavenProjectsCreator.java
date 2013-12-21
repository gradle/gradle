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

package org.gradle.buildinit.plugins.internal.maven;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.internal.SystemProperties;
import org.gradle.mvn3.org.apache.maven.execution.*;
import org.gradle.mvn3.org.apache.maven.project.*;
import org.gradle.mvn3.org.apache.maven.settings.Settings;
import org.gradle.mvn3.org.codehaus.plexus.ContainerConfiguration;
import org.gradle.mvn3.org.codehaus.plexus.DefaultContainerConfiguration;
import org.gradle.mvn3.org.codehaus.plexus.DefaultPlexusContainer;
import org.gradle.mvn3.org.codehaus.plexus.PlexusContainerException;
import org.gradle.mvn3.org.codehaus.plexus.classworlds.ClassWorld;
import org.gradle.mvn3.org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.gradle.mvn3.org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.gradle.mvn3.org.sonatype.aether.RepositorySystemSession;
import org.gradle.mvn3.org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.gradle.util.CollectionUtils;

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

    private Set<MavenProject> createNow(Settings settings, File pomFile) throws PlexusContainerException, PlexusConfigurationException, ComponentLookupException, MavenExecutionRequestPopulationException, ProjectBuildingException {
        //using jarjar for maven3 classes affects the contents of the effective pom
        //references to certain Maven standard plugins contain jarjar in the fqn
        //not sure if this is a problem.
        ContainerConfiguration containerConfiguration = new DefaultContainerConfiguration()
                .setClassWorld(new ClassWorld("plexus.core", ClassWorld.class.getClassLoader()))
                .setName("mavenCore");

        DefaultPlexusContainer container = new DefaultPlexusContainer(containerConfiguration);
        ProjectBuilder builder = container.lookup(ProjectBuilder.class);
        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        final Properties properties = new Properties();
        properties.putAll(SystemProperties.asMap());
        executionRequest.setSystemProperties(properties);
        MavenExecutionRequestPopulator populator = container.lookup(MavenExecutionRequestPopulator.class);
        populator.populateFromSettings(executionRequest, settings);
        populator.populateDefaults(executionRequest);
        ProjectBuildingRequest buildingRequest = executionRequest.getProjectBuildingRequest();
        buildingRequest.setProcessPlugins(false);
        MavenProject mavenProject = builder.build(pomFile, buildingRequest).getProject();
        Set<MavenProject> reactorProjects = new LinkedHashSet<MavenProject>();

        //TODO adding the parent project first because the converter needs it this way ATM. This is oversimplified.
        //the converter should not depend on the order of reactor projects.
        //we should add coverage for nested multi-project builds with multiple parents.
        reactorProjects.add(mavenProject);
        List<ProjectBuildingResult> allProjects = builder.build(ImmutableList.of(pomFile), true, buildingRequest);
        CollectionUtils.collect(allProjects, reactorProjects, new Transformer<MavenProject, ProjectBuildingResult>() {
            public MavenProject transform(ProjectBuildingResult original) {
                return original.getProject();
            }
        });

        MavenExecutionResult result = new DefaultMavenExecutionResult();
        result.setProject(mavenProject);
        RepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        MavenSession session = new MavenSession(container, repoSession, executionRequest, result);
        session.setCurrentProject(mavenProject);

        return reactorProjects;
    }
}
