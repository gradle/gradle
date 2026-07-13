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

import org.apache.maven.settings.Mirror;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ProviderFactory;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class DefaultMavenMirrorResolver implements MavenMirrorResolver {
    public static final String ENABLE_PROPERTY = "org.gradle.internal.mavenMirrors";

    private static final Logger LOGGER = Logging.getLogger(DefaultMavenMirrorResolver.class);
    private static final String WILDCARD_MIRROR_OF = "*";

    private final MavenSettingsProvider settingsProvider;
    private final ProviderFactory providerFactory;

    private volatile boolean computed;
    private @Nullable MirroredRepository wildcardMirror;

    public DefaultMavenMirrorResolver(MavenSettingsProvider settingsProvider, ProviderFactory providerFactory) {
        this.settingsProvider = settingsProvider;
        this.providerFactory = providerFactory;
    }

    @Override
    public Optional<MirroredRepository> mirrorFor(URI original) {
        MirroredRepository mirror = getWildcardMirror();
        if (mirror == null) {
            return Optional.empty();
        }
        String scheme = original.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // Only remote repositories are mirrored, in particular this excludes mavenLocal()
            return Optional.empty();
        }
        if (original.equals(mirror.getUrl())) {
            return Optional.empty();
        }
        return Optional.of(mirror);
    }

    private @Nullable MirroredRepository getWildcardMirror() {
        if (!computed) {
            synchronized (this) {
                if (!computed) {
                    wildcardMirror = computeWildcardMirror();
                    computed = true;
                }
            }
        }
        return wildcardMirror;
    }

    private @Nullable MirroredRepository computeWildcardMirror() {
        if (!isEnabled()) {
            return null;
        }
        // Obtaining the value source registers the settings.xml checksum as a build input,
        // so that the configuration cache is invalidated when the Maven settings change.
        // This only happens when the feature is enabled: the property read above is the
        // only input registered otherwise.
        providerFactory.of(MavenSettingsChecksumValueSource.class, spec -> {}).getOrNull();
        MirroredRepository selected = null;
        try {
            for (Mirror mirror : settingsProvider.buildSettings().getMirrors()) {
                if (!WILDCARD_MIRROR_OF.equals(mirror.getMirrorOf())) {
                    LOGGER.lifecycle("Maven mirror '{}' with mirrorOf '{}' is not supported and will be ignored (only '*' is supported).", mirror.getId(), mirror.getMirrorOf());
                    continue;
                }
                if (selected != null) {
                    LOGGER.lifecycle("Maven mirror '{}' is ignored: mirror '{}' already matches all repositories.", mirror.getId(), selected.getId());
                    continue;
                }
                MirroredRepository candidate = toMirroredRepository(mirror);
                if (candidate != null) {
                    selected = candidate;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Cannot read Maven mirrors from Maven settings, no mirror will be applied.", e);
            return null;
        }
        return selected;
    }

    private static @Nullable MirroredRepository toMirroredRepository(Mirror mirror) {
        try {
            URI url = new URI(mirror.getUrl());
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                LOGGER.lifecycle("Maven mirror '{}' does not use HTTPS: {}", mirror.getId(), url);
            }
            return new MirroredRepository(mirror.getId(), url);
        } catch (URISyntaxException e) {
            LOGGER.lifecycle("Maven mirror '{}' has an invalid URL and will be ignored: {}", mirror.getId(), mirror.getUrl());
            return null;
        }
    }

    private boolean isEnabled() {
        return "true".equals(providerFactory.gradleProperty(ENABLE_PROPERTY).getOrNull());
    }
}
