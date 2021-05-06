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

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultGradleApiSourcesResolver implements GradleApiSourcesResolver {

    private static final String GRADLE_LIBS_REPO_URL = "https://repo.gradle.org/gradle/list/libs-releases";
    private static final String GRADLE_LIBS_REPO_OVERRIDE_VAR = "GRADLE_LIBS_REPO_OVERRIDE";
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(groovy(-.+)?)-(\\d.*?).jar");

    private final Project project;

    public DefaultGradleApiSourcesResolver(Project project) {
        this.project = project;
    }

    @Override
    public File resolveLocalGroovySources(String jarName) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(jarName);
        if (!matcher.matches()) {
            return null;
        }
        VersionNumber version = VersionNumber.parse(matcher.group(3));
        final String artifactName = matcher.group(1);
        MavenArtifactRepository repository = addGradleLibsRepository();
        try {
            return downloadLocalGroovySources(artifactName, version);
        } finally {
            project.getRepositories().remove(repository);
        }
    }

    private File downloadLocalGroovySources(String artifact, VersionNumber version) {
        ArtifactResolutionResult result = project.getDependencies().createArtifactResolutionQuery()
            .forModule("org.codehaus.groovy", artifact, version.toString())
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

    private MavenArtifactRepository addGradleLibsRepository() {
        return project.getRepositories().maven(a -> {
            a.setName("Gradle Libs");
            a.setUrl(gradleLibsRepoUrl());
        });
    }

    private static String gradleLibsRepoUrl() {
        String repoOverride = System.getenv(GRADLE_LIBS_REPO_OVERRIDE_VAR);
        return repoOverride != null ? repoOverride : GRADLE_LIBS_REPO_URL;
    }
}
