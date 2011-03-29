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
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenPomFactory
import org.gradle.api.internal.artifacts.publish.maven.DefaultArtifactPomFactory
import org.gradle.api.internal.artifacts.publish.maven.dependencies.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultPomDependenciesConverter
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomContainer
import org.gradle.api.internal.artifacts.publish.maven.deploy.DefaultArtifactPomContainer
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider
import org.gradle.api.internal.artifacts.publish.maven.deploy.groovy.DefaultGroovyMavenDeployer
import org.gradle.logging.LoggingManagerInternal
import org.gradle.api.internal.artifacts.publish.maven.deploy.BasePomFilterContainer
import org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenInstaller

class DefaultMavenFactory implements MavenFactory {
    private static final DefaultMavenFactory INSTANCE = new DefaultMavenFactory()

    static MavenFactory getInstance() {
        INSTANCE
    }

    ArtifactPomFactory newArtifactPomFactory() {
        new DefaultArtifactPomFactory()
    }

    Factory<MavenPom> newMavenPomFactory(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer mappingContainer,
                                         PomDependenciesConverter dependenciesConverter, FileResolver fileResolver) {
        new DefaultMavenPomFactory(configurationContainer, mappingContainer, dependenciesConverter, fileResolver)
    }

    PomDependenciesConverter newPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter) {
        new DefaultPomDependenciesConverter(excludeRuleConverter)
    }

    ExcludeRuleConverter newExcludeRuleConverter() {
        new DefaultExcludeRuleConverter()
    }

    ArtifactPomContainer newArtifactPomContainer(MavenPomMetaInfoProvider pomMetaInfoProvider, PomFilterContainer filterContainer,
                                                 ArtifactPomFactory pomFactory) {
        new DefaultArtifactPomContainer(pomMetaInfoProvider, filterContainer, pomFactory)
    }

    DefaultGroovyMavenDeployer newGroovyMavenDeployer(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        new DefaultGroovyMavenDeployer(name, pomFilterContainer, artifactPomContainer, loggingManager)
    }

    PomFilterContainer newPomFilterContainer(Factory<MavenPom> mavenPomFactory) {
        new BasePomFilterContainer(mavenPomFactory)
    }

    MavenResolver newMavenInstaller(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        new BaseMavenInstaller(name, pomFilterContainer, artifactPomContainer, loggingManager)
    }
}
