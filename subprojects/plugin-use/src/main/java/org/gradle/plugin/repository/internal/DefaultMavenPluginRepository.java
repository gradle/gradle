/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.repository.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.plugin.repository.MavenPluginRepository;


class DefaultMavenPluginRepository extends AbstractPluginRepository implements MavenPluginRepository {
    private static final String MAVEN = "maven";

    public DefaultMavenPluginRepository(
        FileResolver fileResolver, DependencyResolutionServices dependencyResolutionServices,
        VersionSelectorScheme versionSelectorScheme, AuthenticationSupportedInternal delegate) {
        super(MAVEN, fileResolver, dependencyResolutionServices, versionSelectorScheme, delegate);
    }

    @Override
    protected ArtifactRepository internalCreateArtifactRepository(RepositoryHandler repositoryHandler) {
        return repositoryHandler.maven(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(MavenArtifactRepository mavenArtifactRepository) {
                mavenArtifactRepository.setName(getArtifactRepositoryName());
                mavenArtifactRepository.setUrl(getUrl());
                Credentials credentials = authenticationSupport().getConfiguredCredentials();
                if (credentials != null) {
                    ((AuthenticationSupportedInternal)mavenArtifactRepository).setConfiguredCredentials(credentials);
                    mavenArtifactRepository.authentication(new Action<AuthenticationContainer>() {
                        @Override
                        public void execute(AuthenticationContainer authenticationContainer) {
                            authenticationContainer.addAll(authenticationSupport().getConfiguredAuthentication());
                        }
                    });
                }
            }
        });
    }
}
