/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.mvnsettings;

import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider.MirroredRepository;
import org.gradle.util.internal.IncubationLogger;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.util.Optional;

@NullMarked
public class DefaultMavenMirrorResolver implements MavenMirrorResolver {
    private static final String CENTRAL_REPOSITORY_ID = "central";
    private static final String CENTRAL_REPOSITORY_URL = normalizeUrl(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);

    private final MavenSettingsProvider settingsProvider;
    private final StartParameterInternal startParameter;

    public DefaultMavenMirrorResolver(MavenSettingsProvider settingsProvider, StartParameterInternal startParameter) {
        this.settingsProvider = settingsProvider;
        this.startParameter = startParameter;
    }

    @Override
    public Optional<MirroredRepository> mirrorFor(URI original, String repositoryName) {
        if (!isEnabled()) {
            // The whole feature is off: no settings are read and nothing is declared as a
            // configuration cache input, so this costs one boolean read off the start parameter
            return Optional.empty();
        }
        // Logged once per build, however many repositories consult the mirrors
        IncubationLogger.incubatingFeatureUsed("Reusing Maven mirror settings");
        String scheme = original.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // Only remote repositories are mirrored, in particular this excludes mavenLocal().
            // Checked before the settings are read so that a file based repository does not pay
            // for reading them at all.
            return Optional.empty();
        }
        String effectiveId = effectiveIdOf(original, repositoryName);
        for (MirroredRepository mirror : settingsProvider.getMirrors()) {
            if (MirrorOfMatcher.matches(mirror.getMirrorOf(), effectiveId, original)) {
                if (original.equals(mirror.getUrl())) {
                    return Optional.empty();
                }
                return Optional.of(mirror);
            }
        }
        return Optional.empty();
    }

    /**
     * The id a repository is matched by: {@code central} when its URL is Maven Central's
     * (the id the Super POM gives that URL), the Gradle repository name otherwise.
     */
    private static String effectiveIdOf(URI url, String repositoryName) {
        return CENTRAL_REPOSITORY_URL.equals(normalizeUrl(url.toString())) ? CENTRAL_REPOSITORY_ID : repositoryName;
    }

    private static String normalizeUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean isEnabled() {
        return startParameter.isSharedMavenMirrorSettings();
    }
}
