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
package org.gradle.api.artifacts.maven

import org.gradle.api.internal.Factory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomFactory
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter
import org.gradle.api.internal.artifacts.publish.maven.dependencies.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomContainer
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider
import org.gradle.logging.LoggingManagerInternal
import org.gradle.api.internal.artifacts.publish.maven.LocalMavenCacheLocator
import org.gradle.api.artifacts.Configuration

class DefaultMavenFactory implements MavenFactory {
    private final ClassLoader classLoader

    DefaultMavenFactory(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    ArtifactPomFactory newArtifactPomFactory() {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.DefaultArtifactPomFactory").newInstance()
    }

    Factory<MavenPom> newMavenPomFactory(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer mappingContainer,
                                         PomDependenciesConverter dependenciesConverter, FileResolver fileResolver) {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.DefaultMavenPomFactory").newInstance(
                configurationContainer, mappingContainer, dependenciesConverter, fileResolver)
    }

    PomDependenciesConverter newPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter) {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultPomDependenciesConverter").newInstance(excludeRuleConverter)
    }

    ExcludeRuleConverter newExcludeRuleConverter() {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultExcludeRuleConverter").newInstance()
    }

    ArtifactPomContainer newArtifactPomContainer(MavenPomMetaInfoProvider pomMetaInfoProvider, PomFilterContainer filterContainer,
                                                 ArtifactPomFactory pomFactory) {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.deploy.DefaultArtifactPomContainer").newInstance(
                pomMetaInfoProvider, filterContainer, pomFactory)
    }

    GroovyMavenDeployer newGroovyMavenDeployer(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.deploy.groovy.DefaultGroovyMavenDeployer").newInstance(
                name, pomFilterContainer, artifactPomContainer, loggingManager)
    }

    PomFilterContainer newPomFilterContainer(Factory<MavenPom> mavenPomFactory) {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.deploy.BasePomFilterContainer").newInstance(mavenPomFactory)
    }

    MavenResolver newMavenInstaller(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenInstaller").newInstance(
                name, pomFilterContainer, artifactPomContainer, loggingManager)
    }

    LocalMavenCacheLocator newLocalMavenCacheLocator() {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.DefaultLocalMavenCacheLocator").newInstance()
    }

    Conf2ScopeMappingContainer newConf2ScopeMappingContainer(Map<Configuration, Conf2ScopeMapping> mappings) {
        classLoader.loadClass("org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer").newInstance(mappings)
    }
}
