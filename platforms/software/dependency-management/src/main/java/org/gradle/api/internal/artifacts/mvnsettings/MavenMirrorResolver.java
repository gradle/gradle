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

import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider.MirroredRepository;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.util.Optional;

/**
 * Selects which of the mirrors declared in the local Maven {@code settings.xml} applies to a
 * remote Maven repository URL. Parsing the settings is {@link MavenSettingsProvider}'s job;
 * this only matches what it produced.
 *
 * <p>Prototype: only enabled when the {@code org.gradle.mirror.maven.settings}
 * Gradle property is set to {@code true}.
 *
 * @see <a href="https://maven.apache.org/guides/mini/guide-mirror-settings.html">Using Mirrors for Repositories</a>
 */
@NullMarked
@ServiceScope(Scope.BuildTree.class)
public interface MavenMirrorResolver {

    /**
     * Returns the mirror that should replace the given repository URL, if any.
     *
     * <p>Mirrors are matched in settings declaration order against the repository's
     * effective id — {@code central} when the URL is Maven Central's, the Gradle
     * repository name otherwise — using the
     * <a href="https://maven.apache.org/guides/mini/guide-mirror-settings.html#advanced-mirror-specification">Maven
     * {@code mirrorOf} grammar</a>; the first match wins. Empty when the feature is
     * disabled, no mirror matches, the URL is not a remote ({@code http}/{@code https})
     * URL, or the URL is already the mirror URL. A returned mirror may be
     * {@link MirroredRepository#isBlocked() blocked}, in which case the repository must
     * not be used at all.
     */
    Optional<MirroredRepository> mirrorFor(URI original, String repositoryName);
}
