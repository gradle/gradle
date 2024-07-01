/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.project.ProjectInternal.DetachedResolver;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.util.internal.GroovyDependencyUtil.groovyGroupName;

public class DefaultGradleApiSourcesResolver implements GradleApiSourcesResolver {

    @VisibleForTesting
    static final String GRADLE_LIBS_REPO_URL = "https://repo.gradle.org/gradle/list/libs-releases";
    private static final String GRADLE_LIBS_REPO_OVERRIDE_VAR = "GRADLE_LIBS_REPO_OVERRIDE";
    private static final String GRADLE_LIBS_REPO_OVERRIDE_PROJECT_PROPERTY = "org.gradle.libraries.sourceRepository.url";
    private static final String GRADLE_LIBS_REPO_CREDENTIALS_PROJECT_PROPERTY = "org.gradle.libraries.sourceRepository.credentialsProperties";
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(groovy(-.+?)?)-(\\d.+?)\\.jar");

    private final DetachedResolver resolver;

    public DefaultGradleApiSourcesResolver(ProviderFactory providers, DetachedResolver resolver) {
        this.resolver = resolver;
        addGradleLibsRepository(providers);
    }

    @Override
    public File resolveLocalGroovySources(String jarName) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(jarName);
        if (!matcher.matches()) {
            return null;
        }
        VersionNumber version = VersionNumber.parse(matcher.group(3));
        final String artifactName = matcher.group(1);
        return downloadLocalGroovySources(artifactName, version);
    }

    private File downloadLocalGroovySources(String artifact, VersionNumber version) {
        ArtifactResolutionResult result = resolver.getDependencies().createArtifactResolutionQuery()
            .forModule(groovyGroupName(version), artifact, version.toString())
            .withArtifacts(JvmLibrary.class, Collections.singletonList(SourcesArtifact.class))
            .execute();

        for (ComponentArtifactsResult artifactsResult : result.getResolvedComponents()) {
            for (ArtifactResult artifactResult : artifactsResult.getArtifacts(SourcesArtifact.class)) {
                if (artifactResult instanceof ResolvedArtifactResult) {
                    return ((ResolvedArtifactResult) artifactResult).getFile();
                }
            }
        }
        return null;
    }

    private void addGradleLibsRepository(ProviderFactory providers) {
        resolver.getRepositories().maven(a -> configureLibsRepo(a, providers));
    }

    @VisibleForTesting
    static void configureLibsRepo(MavenArtifactRepository repository, ProviderFactory providers) {
        repository.setName("Gradle Libs");

        String url = gradleLibsRepoUrl(providers);
        repository.setUrl(url);
        if (GRADLE_LIBS_REPO_URL.equals(url)) {
            // We don't want to accidentally send the credentials to the public repository.
            return;
        }

        String credentialsPropertyNames = providers.gradleProperty(GRADLE_LIBS_REPO_CREDENTIALS_PROJECT_PROPERTY).getOrNull();
        if (credentialsPropertyNames != null && !credentialsPropertyNames.isEmpty()) {
            String[] userAndPasswordKeys = credentialsPropertyNames.split(":", -1);
            if (userAndPasswordKeys.length != 2) {
                throw new IllegalArgumentException("Expected 'userPropertyName:passwordPropertyName' format for " + GRADLE_LIBS_REPO_CREDENTIALS_PROJECT_PROPERTY);
            }
            repository.credentials(credentials -> {
                credentials.setUsername(providers.gradleProperty(userAndPasswordKeys[0].trim()).getOrNull());
                credentials.setPassword(providers.gradleProperty(userAndPasswordKeys[1].trim()).getOrNull());
            });
        }
    }

    private static String gradleLibsRepoUrl(ProviderFactory providers) {
        String repoOverride = providers.gradleProperty(GRADLE_LIBS_REPO_OVERRIDE_PROJECT_PROPERTY).getOrNull();
        if (repoOverride != null) {
            return repoOverride;
        }

        repoOverride = providers.environmentVariable(GRADLE_LIBS_REPO_OVERRIDE_VAR).getOrNull();
        if (repoOverride != null) {
            return repoOverride;
        }

        return GRADLE_LIBS_REPO_URL;
    }
}
