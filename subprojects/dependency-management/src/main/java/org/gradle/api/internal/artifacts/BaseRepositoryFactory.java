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

import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/**
 * ResolvedArtifactTransformer for {@link org.gradle.api.artifacts.repositories.ArtifactRepository} implementations.
 */
public interface BaseRepositoryFactory {

    String PLUGIN_PORTAL_DEFAULT_URL = "https://plugins.gradle.org/m2";
    String PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY = "org.gradle.internal.plugins.portal.url.override";

    FlatDirectoryArtifactRepository createFlatDirRepository();

    ArtifactRepository createGradlePluginPortal();

    MavenArtifactRepository createMavenLocalRepository();

    MavenArtifactRepository createJCenterRepository();

    MavenArtifactRepository createMavenCentralRepository();

    MavenArtifactRepository createGoogleRepository();

    IvyArtifactRepository createIvyRepository();

    MavenArtifactRepository createMavenRepository();
}
