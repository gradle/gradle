/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.specs.Spec;

public class Blah implements Plugin<Gradle> {
    @Override
    public void apply(Gradle gradle) {
        Spec<MavenArtifactRepository> canBeMirrored = r -> {
            // TODO: Plugin portal
            // TODO: Disallow mirroring if incompatible features are used
            return r.getName().equals(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME);
        };

        Action<MavenArtifactRepository> useMirror = r -> {
            String mirrorAvailable = System.getProperty("org.gradle.mirror.mavenCentral");
            if (mirrorAvailable != null) {
                r.setUrl(mirrorAvailable);
            }
        };
        Action<RepositoryHandler> configureMirror = repositories -> {
            repositories.withType(MavenArtifactRepository.class)
                .matching(canBeMirrored)
                .configureEach(useMirror);
        };
        gradle.beforeSettings(settings -> {
            configureMirror.execute(settings.getBuildscript().getRepositories());
            configureMirror.execute(settings.getPluginManagement().getRepositories());
        });
        gradle.settingsEvaluated(settings -> {
            configureMirror.execute(settings.getDependencyResolutionManagement().getRepositories());
        });
        gradle.beforeProject(p -> {
            configureMirror.execute(p.getBuildscript().getRepositories());
        });
        gradle.afterProject(p -> {
            configureMirror.execute(p.getRepositories());
        });
    }
}
