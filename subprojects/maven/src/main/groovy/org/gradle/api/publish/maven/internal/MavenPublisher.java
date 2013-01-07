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

package org.gradle.api.publish.maven.internal;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenDeployer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.Cast;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.publication.maven.internal.DefaultConf2ScopeMappingContainer;
import org.gradle.api.publication.maven.internal.DeployerFactory;
import org.gradle.api.publication.maven.internal.PomDependenciesConverter;
import org.gradle.api.publication.maven.internal.ant.DefaultExcludeRuleConverter;
import org.gradle.api.publication.maven.internal.ant.DefaultPomDependenciesConverter;
import org.gradle.api.publication.maven.internal.ant.ProjectDependencyArtifactIdExtractorHack;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.internal.Factory;

import java.util.Collections;
import java.util.List;

public class MavenPublisher {

    private final DeployerFactory deployerFactory;
    private final Factory<Configuration> configurationFactory;
    private final ArtifactPublisher artifactPublisher;
    private final PomDependenciesConverter pomDependenciesConverter = new MavenPublishPomDependenciesConverter();

    public MavenPublisher(DeployerFactory deployerFactory, Factory<Configuration> configurationFactory, ArtifactPublisher artifactPublisher) {
        this.deployerFactory = deployerFactory;
        this.configurationFactory = configurationFactory;
        this.artifactPublisher = artifactPublisher;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        MavenDeployer deployer = deployerFactory.createMavenDeployer();
        new MavenDeployerConfigurer(artifactRepository).execute(deployer);
        doPublish(publication, deployer);
    }

    private void doPublish(MavenNormalizedPublication publication, MavenDeployer deployer) {
        Configuration publishConfiguration = createPopulatedConfiguration(publication);

        MavenPom deployerPom = deployer.getPom();
        copyIdentity(publication, deployerPom);
        copyDependencies(publishConfiguration, deployerPom);
        deployerPom.withXml(publication.getPomWithXmlAction());

        Module module = new MavenProjectIdentityModuleAdapter(publication);

        DependencyResolver dependencyResolver = Cast.cast(DependencyResolver.class, deployer);
        artifactPublisher.publish(Collections.singleton(dependencyResolver), module, Collections.singleton(publishConfiguration), null);
    }

    private void copyIdentity(MavenProjectIdentity projectIdentity, MavenPom pom) {
        pom.setArtifactId(projectIdentity.getArtifactId());
        pom.setGroupId(projectIdentity.getGroupId());
        pom.setVersion(projectIdentity.getVersion());
        pom.setPackaging(projectIdentity.getPackaging());
    }

    private void copyDependencies(Configuration configuration, MavenPom pom) {
        Conf2ScopeMappingContainer mappingContainer = new DefaultConf2ScopeMappingContainer();
        mappingContainer.addMapping(0, configuration, "runtime");
        List<?> mavenDependencies = pomDependenciesConverter.convert(mappingContainer, Collections.singleton(configuration));
        pom.setDependencies(mavenDependencies);
    }

    Configuration createPopulatedConfiguration(MavenNormalizedPublication publication) {
        Configuration configuration = configurationFactory.create();
        for (PublishArtifact artifact : publication.getArtifacts()) {
            configuration.getArtifacts().add(artifact);
        }
        for (Dependency runtimeDependency : publication.getRuntimeDependencies()) {
            configuration.getDependencies().add(runtimeDependency);
        }
        return configuration;
    }

    private static class MavenPublishPomDependenciesConverter extends DefaultPomDependenciesConverter {
        public MavenPublishPomDependenciesConverter() {
            super(new DefaultExcludeRuleConverter());
        }

        @Override
        protected String determineProjectDependencyArtifactId(ProjectDependency dependency) {
            // Don't use artifact id hacks when the target project has the maven-publish plugin applied
            if (dependency.getDependencyProject().getPlugins().hasPlugin(MavenPublishPlugin.class)) {
                return dependency.getDependencyProject().getName();
            }
            return new ProjectDependencyArtifactIdExtractorHack(dependency).extract();
        }
    }
}
