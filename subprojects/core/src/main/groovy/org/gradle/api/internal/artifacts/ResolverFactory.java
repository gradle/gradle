/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.ArtifactRepository;
import org.gradle.api.artifacts.dsl.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.dsl.IvyArtifactRepository;
import org.gradle.api.artifacts.dsl.MavenArtifactRepository;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider;
import org.gradle.api.internal.file.FileResolver;

/**
 * @author Hans Dockter
 */
public interface ResolverFactory {
    ArtifactRepository createRepository(Object userDescription);

    FlatDirectoryArtifactRepository createFlatDirRepository();

    MavenArtifactRepository createMavenLocalRepository();

    MavenArtifactRepository createMavenCentralRepository();

    GroovyMavenDeployer createMavenDeployer(MavenPomMetaInfoProvider pomMetaInfoProvider, ConfigurationContainer configurationContainer,
                                            Conf2ScopeMappingContainer scopeMapping, FileResolver fileResolver);

    MavenResolver createMavenInstaller(MavenPomMetaInfoProvider pomMetaInfoProvider, ConfigurationContainer configurationContainer,
                                       Conf2ScopeMappingContainer scopeMapping, FileResolver fileResolver);

    IvyArtifactRepository createIvyRepository();

    MavenArtifactRepository createMavenRepository();
}
