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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.MavenDeployer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.Cast;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.publication.maven.internal.DeployerFactory;
import org.gradle.internal.Factory;

import java.util.Collections;

public class MavenPublisher {

    private final DeployerFactory deployerFactory;
    private final Factory<Configuration> configurationFactory;
    private final Transformer<ArtifactPublisher, DependencyResolver> artifactPublisherFactory;

    public MavenPublisher(DeployerFactory deployerFactory, Factory<Configuration> configurationFactory, Transformer<ArtifactPublisher, DependencyResolver> artifactPublisherFactory) {
        this.deployerFactory = deployerFactory;
        this.configurationFactory = configurationFactory;
        this.artifactPublisherFactory = artifactPublisherFactory;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        MavenDeployer deployer = deployerFactory.createMavenDeployer();
        new MavenDeployerConfigurer(artifactRepository).execute(deployer);
        doPublish(publication, deployer);
    }

    private void doPublish(MavenNormalizedPublication publication, MavenDeployer deployer) {
        MavenPom deployerPom = deployer.getPom();
        copyIdentity(publication, deployerPom);
        deployerPom.withXml(publication.getPomWithXmlAction());

        Module module = new MavenProjectIdentityModuleAdapter(publication);
        Configuration artifactsAsConfiguration = createPopulatedConfiguration(publication.getArtifacts());

        DependencyResolver dependencyResolver = Cast.cast(DependencyResolver.class, deployer);
        ArtifactPublisher artifactPublisher = artifactPublisherFactory.transform(dependencyResolver);
        artifactPublisher.publish(module, Collections.singleton(artifactsAsConfiguration), null);
    }

    private void copyIdentity(MavenProjectIdentity projectIdentity, MavenPom pom) {
        pom.setArtifactId(projectIdentity.getArtifactId());
        pom.setGroupId(projectIdentity.getGroupId());
        pom.setVersion(projectIdentity.getVersion());
        pom.setPackaging(projectIdentity.getPackaging());
    }

    Configuration createPopulatedConfiguration(Iterable<PublishArtifact> artifacts) {
        Configuration configuration = configurationFactory.create();
        for (PublishArtifact artifact : artifacts) {
            configuration.getArtifacts().add(artifact);
        }
        return configuration;
    }

}
