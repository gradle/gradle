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
package org.gradle.api.artifacts.maven;

import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomFactory;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider;
import org.gradle.api.internal.artifacts.publish.maven.deploy.ArtifactPomContainer;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.api.internal.artifacts.publish.maven.LocalMavenCacheLocator;
import org.gradle.api.artifacts.Configuration;

import java.util.Map;

/**
 * Factory for various types related to Maven dependency management.
 * The motivation for having this factory is to allow implementation
 * types, and more importantly their dependencies, to be loaded from a
 * different (coreImpl) class loader. This helps to prevent version conflicts,
 * for example between Maven 2 and Maven 3 libraries.
 */
public interface MavenFactory {
    ArtifactPomFactory createArtifactPomFactory();

    Factory<MavenPom> createMavenPomFactory(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer conf2ScopeMappingContainer,
                                            PomDependenciesConverter pomDependenciesConverter, FileResolver fileResolver);

    PomDependenciesConverter createPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter);

    ExcludeRuleConverter createExcludeRuleConverter();

    ArtifactPomContainer createArtifactPomContainer(MavenPomMetaInfoProvider pomMetaInfoProvider, PomFilterContainer filterContainer,
                                                    ArtifactPomFactory pomFactory);

    GroovyMavenDeployer createGroovyMavenDeployer(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager);

    PomFilterContainer createPomFilterContainer(Factory<MavenPom> mavenPomFactory);

    MavenResolver createMavenInstaller(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager);

    LocalMavenCacheLocator createLocalMavenCacheLocator();

    Conf2ScopeMappingContainer createConf2ScopeMappingContainer(Map<Configuration, Conf2ScopeMapping> mappings);
}