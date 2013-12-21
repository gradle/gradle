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
package org.gradle.api.publication.maven.internal;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publication.maven.internal.ant.BaseMavenInstaller;
import org.gradle.api.publication.maven.internal.ant.DefaultGroovyMavenDeployer;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;

public class DefaultDeployerFactory implements DeployerFactory {
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final MavenFactory mavenFactory;
    private final FileResolver fileResolver;
    private final MavenPomMetaInfoProvider pomMetaInfoProvider;
    private final ConfigurationContainer configurationContainer;
    private final Conf2ScopeMappingContainer scopeMapping;

    public DefaultDeployerFactory(MavenFactory mavenFactory, Factory<LoggingManagerInternal> loggingManagerFactory, FileResolver fileResolver, MavenPomMetaInfoProvider pomMetaInfoProvider,
                                  ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer scopeMapping) {
        this.mavenFactory = mavenFactory;
        this.loggingManagerFactory = loggingManagerFactory;
        this.fileResolver = fileResolver;
        this.pomMetaInfoProvider = pomMetaInfoProvider;
        this.configurationContainer = configurationContainer;
        this.scopeMapping = scopeMapping;
    }

    public DefaultGroovyMavenDeployer createMavenDeployer() {
        PomFilterContainer pomFilterContainer = createPomFilterContainer(
                mavenFactory.createMavenPomFactory(configurationContainer, scopeMapping, fileResolver));
        return new DefaultGroovyMavenDeployer(pomFilterContainer, createArtifactPomContainer(
                pomMetaInfoProvider, pomFilterContainer, createArtifactPomFactory()), loggingManagerFactory.create());
    }

    public MavenResolver createMavenInstaller() {
        PomFilterContainer pomFilterContainer = createPomFilterContainer(
                mavenFactory.createMavenPomFactory(configurationContainer, scopeMapping, fileResolver));
        return new BaseMavenInstaller(pomFilterContainer, createArtifactPomContainer(pomMetaInfoProvider,
                pomFilterContainer, createArtifactPomFactory()), loggingManagerFactory.create());
    }

    private PomFilterContainer createPomFilterContainer(Factory<MavenPom> mavenPomFactory) {
        return new BasePomFilterContainer(mavenPomFactory);
    }

    private ArtifactPomFactory createArtifactPomFactory() {
        return new DefaultArtifactPomFactory();
    }

    private ArtifactPomContainer createArtifactPomContainer(MavenPomMetaInfoProvider pomMetaInfoProvider, PomFilterContainer filterContainer,
                                                            ArtifactPomFactory pomFactory) {
        return new DefaultArtifactPomContainer(pomMetaInfoProvider, filterContainer, pomFactory);
    }

}
