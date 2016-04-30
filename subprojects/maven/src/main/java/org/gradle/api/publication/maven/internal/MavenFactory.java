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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.internal.Factory;
import org.gradle.api.internal.file.FileResolver;

import java.util.Map;

/**
 * Factory for various types related to Maven dependency management.
 */
public interface MavenFactory {
    Factory<MavenPom> createMavenPomFactory(ConfigurationContainer configurationContainer, Map<Configuration, Conf2ScopeMapping> mappings, FileResolver fileResolver);

    Factory<MavenPom> createMavenPomFactory(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer conf2ScopeMappingContainer, FileResolver fileResolver);

    Conf2ScopeMappingContainer createConf2ScopeMappingContainer(Map<Configuration, Conf2ScopeMapping> mappings);
}