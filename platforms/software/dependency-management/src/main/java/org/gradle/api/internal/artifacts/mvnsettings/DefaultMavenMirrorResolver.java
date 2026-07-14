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
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.util.internal.MavenUtil;
import org.jspecify.annotations.Nullable;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

import java.io.File;
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
        // Obtaining the value source registers the settings.xml and settings-security.xml
        // checksums as a build input, so that the configuration cache is invalidated when
        // the Maven settings change. This only happens when the feature is enabled: the
        // property read above is the only input registered otherwise.
        providerFactory.of(MavenSettingsChecksumValueSource.class, spec -> {}).getOrNull();
        MirroredRepository selected = null;
        try {
            Settings settings = settingsProvider.buildSettings();
            for (Mirror mirror : settings.getMirrors()) {
                if (!WILDCARD_MIRROR_OF.equals(mirror.getMirrorOf())) {
                    LOGGER.lifecycle("Maven mirror '{}' with mirrorOf '{}' is not supported and will be ignored (only '*' is supported).", mirror.getId(), mirror.getMirrorOf());
                    continue;
                }
                if (selected != null) {
                    LOGGER.lifecycle("Maven mirror '{}' is ignored: mirror '{}' already matches all repositories.", mirror.getId(), selected.getId());
                    continue;
                }
                MirroredRepository candidate = toMirroredRepository(mirror, settings);
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

    private @Nullable MirroredRepository toMirroredRepository(Mirror mirror, Settings settings) {
        try {
            URI url = new URI(mirror.getUrl());
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                LOGGER.lifecycle("Maven mirror '{}' does not use HTTPS: {}", mirror.getId(), url);
            }
            return new MirroredRepository(mirror.getId(), url, resolveCredentials(mirror.getId(), settings));
        } catch (URISyntaxException e) {
            LOGGER.lifecycle("Maven mirror '{}' has an invalid URL and will be ignored: {}", mirror.getId(), mirror.getUrl());
            return null;
        }
    }

    /**
     * Resolves the credentials for a mirror: the {@code <mirrorId>Username}/{@code <mirrorId>Password}
     * Gradle properties win over the settings.xml {@code <server>} entry matching the mirror id,
     * whose password may be encrypted with the Maven master password.
     */
    private @Nullable MirrorCredentials resolveCredentials(String mirrorId, Settings settings) {
        String usernameProperty = mirrorId + "Username";
        String passwordProperty = mirrorId + "Password";
        String username = providerFactory.gradleProperty(usernameProperty).getOrNull();
        String password = providerFactory.gradleProperty(passwordProperty).getOrNull();
        if (username != null && password != null) {
            LOGGER.lifecycle("Using credentials from Gradle properties '{}' and '{}' for Maven mirror '{}'.", usernameProperty, passwordProperty, mirrorId);
            return new MirrorCredentials(username, password);
        }
        if (username != null || password != null) {
            LOGGER.lifecycle("Ignoring partial credentials for Maven mirror '{}': both the '{}' and '{}' Gradle properties must be set.", mirrorId, usernameProperty, passwordProperty);
        }

        Server server = settings.getServer(mirrorId);
        if (server == null) {
            return null;
        }
        try {
            String decryptedPassword = decryptPassword(server.getPassword());
            LOGGER.lifecycle("Using credentials from the Maven settings server entry '{}' for Maven mirror '{}'.", server.getId(), mirrorId);
            return new MirrorCredentials(server.getUsername(), decryptedPassword);
        } catch (Exception e) {
            LOGGER.lifecycle("Cannot decrypt the password of the Maven settings server entry '{}', continuing without credentials for Maven mirror '{}'. " +
                "Set the '{}' and '{}' Gradle properties to provide the credentials directly. ({})", server.getId(), mirrorId, usernameProperty, passwordProperty, e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts a Maven-encrypted ({@code {...}}-wrapped) password using the same mechanism as
     * Maven itself: the master password from settings-security.xml, honoring the
     * {@code settings.security} system property for its location. Plaintext values pass through
     * untouched, without reading settings-security.xml at all.
     */
    private static @Nullable String decryptPassword(@Nullable String password) throws Exception {
        if (password == null) {
            return null;
        }
        DefaultSecDispatcher secDispatcher = new DefaultSecDispatcher(new DefaultPlexusCipher());
        secDispatcher.setConfigurationFile(defaultSecuritySettingsFile().getAbsolutePath());
        return secDispatcher.decrypt(password);
    }

    static File defaultSecuritySettingsFile() {
        return new File(MavenUtil.getUserMavenDir(), "settings-security.xml");
    }

    private boolean isEnabled() {
        return "true".equals(providerFactory.gradleProperty(ENABLE_PROPERTY).getOrNull());
    }
}
