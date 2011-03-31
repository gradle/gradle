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
package org.gradle.api.internal.artifacts.publish.maven;

import org.gradle.api.artifacts.maven.*;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomFactory;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultPomDependenciesConverter;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.publish.maven.deploy.DefaultArtifactPomContainer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.groovy.DefaultGroovyMavenDeployer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.BasePomFilterContainer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenInstaller;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.logging.LoggingManagerInternal;

import java.util.Map;

public class DefaultMavenFactory implements MavenFactory {
    public ArtifactPomFactory createArtifactPomFactory() {
        return new DefaultArtifactPomFactory();
    }

    public Factory<MavenPom> createMavenPomFactory(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer mappingContainer,
                                                   PomDependenciesConverter dependenciesConverter, FileResolver fileResolver) {
        return new DefaultMavenPomFactory(configurationContainer, mappingContainer, dependenciesConverter, fileResolver);
    }

    public PomDependenciesConverter createPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter) {
        return new DefaultPomDependenciesConverter(excludeRuleConverter);
    }

    public ExcludeRuleConverter createExcludeRuleConverter() {
        return new DefaultExcludeRuleConverter();
    }

    public ArtifactPomContainer createArtifactPomContainer(MavenPomMetaInfoProvider pomMetaInfoProvider, PomFilterContainer filterContainer,
                                                           ArtifactPomFactory pomFactory) {
        return new DefaultArtifactPomContainer(pomMetaInfoProvider, filterContainer, pomFactory);
    }

    public GroovyMavenDeployer createGroovyMavenDeployer(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        return new DefaultGroovyMavenDeployer(name, pomFilterContainer, artifactPomContainer, loggingManager);
    }

    public PomFilterContainer createPomFilterContainer(Factory<MavenPom> mavenPomFactory) {
        return new BasePomFilterContainer(mavenPomFactory);
    }

    public MavenResolver createMavenInstaller(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        return new BaseMavenInstaller(name, pomFilterContainer, artifactPomContainer, loggingManager);
    }

    public LocalMavenCacheLocator createLocalMavenCacheLocator() {
        return new DefaultLocalMavenCacheLocator();
    }

    public Conf2ScopeMappingContainer createConf2ScopeMappingContainer(Map<Configuration, Conf2ScopeMapping> mappings) {
        return new DefaultConf2ScopeMappingContainer(mappings);
    }
}
