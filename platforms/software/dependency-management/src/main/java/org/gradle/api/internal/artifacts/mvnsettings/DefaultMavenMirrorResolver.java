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
import org.codehaus.plexus.util.xml.Xpp3Dom;
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
        String mirrorId = mirror.getId();
        try {
            URI url = new URI(mirror.getUrl());
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                LOGGER.lifecycle("Maven mirror '{}' does not use HTTPS: {}", mirrorId, url);
            }
            MirrorCredentials credentials = resolveCredentials(mirrorId, settings);
            MirrorHttpHeader httpHeader = credentials == null ? resolveHttpHeader(mirrorId, settings) : null;
            return new MirroredRepository(mirrorId, url, credentials, httpHeader);
        } catch (URISyntaxException e) {
            LOGGER.lifecycle("Maven mirror '{}' has an invalid URL and will be ignored: {}", mirrorId, mirror.getUrl());
            return null;
        }
    }

    /**
     * Resolves the username/password credentials for a mirror: the
     * {@code <mirrorId>Username}/{@code <mirrorId>Password} Gradle properties win over the
     * settings.xml {@code <server>} entry matching the mirror id, whose password may be
     * encrypted with the Maven master password.
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
        if (server == null || (server.getUsername() == null && server.getPassword() == null)) {
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
     * Resolves the HTTP header a mirror authenticates with, from the
     * {@code <configuration><httpHeaders>} block of the settings.xml {@code <server>} entry
     * matching the mirror id. Only consulted when no username/password credentials apply.
     *
     * <p>Only the first declared header is used; Maven sends all of them, but Gradle's
     * transport attaches a single header credential per host.
     *
     * <p>Unlike Maven, which never decrypts wagon configuration values, an encrypted
     * ({@code {...}}-wrapped) header value is decrypted like a password.
     */
    private @Nullable MirrorHttpHeader resolveHttpHeader(String mirrorId, Settings settings) {
        Server server = settings.getServer(mirrorId);
        if (server == null || !(server.getConfiguration() instanceof Xpp3Dom)) {
            return null;
        }
        Xpp3Dom httpHeaders = ((Xpp3Dom) server.getConfiguration()).getChild("httpHeaders");
        if (httpHeaders == null) {
            return null;
        }
        Xpp3Dom[] properties = httpHeaders.getChildren("property");
        if (properties.length == 0) {
            return null;
        }
        String name = childValue(properties[0], "name");
        String value = childValue(properties[0], "value");
        if (name == null || value == null) {
            LOGGER.lifecycle("Ignoring malformed httpHeaders configuration of the Maven settings server entry '{}' for Maven mirror '{}': each property needs a name and a value.", server.getId(), mirrorId);
            return null;
        }
        if (properties.length > 1) {
            LOGGER.lifecycle("Only the first HTTP header ('{}') of the Maven settings server entry '{}' is applied to Maven mirror '{}'; {} additional header(s) are ignored.", name, server.getId(), mirrorId, properties.length - 1);
        }
        try {
            String decryptedValue = decryptPassword(value);
            LOGGER.lifecycle("Using HTTP header '{}' from the Maven settings server entry '{}' for Maven mirror '{}'.", name, server.getId(), mirrorId);
            return new MirrorHttpHeader(name, decryptedValue);
        } catch (Exception e) {
            LOGGER.lifecycle("Cannot decrypt the value of HTTP header '{}' of the Maven settings server entry '{}', continuing without credentials for Maven mirror '{}'. ({})", name, server.getId(), mirrorId, e.getMessage());
            return null;
        }
    }

    private static @Nullable String childValue(Xpp3Dom parent, String childName) {
        Xpp3Dom child = parent.getChild(childName);
        return child == null ? null : child.getValue();
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
