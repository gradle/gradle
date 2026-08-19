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

import com.google.common.collect.ImmutableList;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.internal.StartParameterInternal;
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
import java.util.List;
import java.util.Optional;

public class DefaultMavenMirrorResolver implements MavenMirrorResolver {
    private static final Logger LOGGER = Logging.getLogger(DefaultMavenMirrorResolver.class);
    private static final String CENTRAL_REPOSITORY_ID = "central";
    private static final String CENTRAL_REPOSITORY_URL = normalizeUrl(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);

    private final MavenSettingsProvider settingsProvider;
    private final ProviderFactory providerFactory;
    private final StartParameterInternal startParameter;

    private volatile boolean computed;
    private List<MirrorCandidate> mirrors = ImmutableList.of();

    public DefaultMavenMirrorResolver(MavenSettingsProvider settingsProvider, ProviderFactory providerFactory, StartParameterInternal startParameter) {
        this.settingsProvider = settingsProvider;
        this.providerFactory = providerFactory;
        this.startParameter = startParameter;
    }

    @Override
    public Optional<MirroredRepository> mirrorFor(URI original, String repositoryName) {
        List<MirrorCandidate> candidates = getMirrors();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String scheme = original.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // Only remote repositories are mirrored, in particular this excludes mavenLocal()
            return Optional.empty();
        }
        String effectiveId = effectiveIdOf(original, repositoryName);
        for (MirrorCandidate candidate : candidates) {
            if (MirrorOfMatcher.matches(candidate.mirrorOf, effectiveId, original)) {
                if (original.equals(candidate.mirror.getUrl())) {
                    return Optional.empty();
                }
                return Optional.of(candidate.mirror);
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

    private List<MirrorCandidate> getMirrors() {
        if (!computed) {
            synchronized (this) {
                if (!computed) {
                    mirrors = computeMirrors();
                    computed = true;
                }
            }
        }
        return mirrors;
    }

    private List<MirrorCandidate> computeMirrors() {
        if (!isEnabled()) {
            return ImmutableList.of();
        }
        // Obtaining the value source registers the settings.xml and settings-security.xml
        // checksums as a build input, so that the configuration cache is invalidated when
        // the Maven settings change. This only happens when the feature is enabled, so a
        // build with the option off registers no input at all.
        providerFactory.of(MavenSettingsChecksumValueSource.class, spec -> {}).getOrNull();
        ImmutableList.Builder<MirrorCandidate> result = ImmutableList.builder();
        try {
            Settings settings = settingsProvider.buildSettings();
            for (Mirror mirror : settings.getMirrors()) {
                MirroredRepository candidate = toMirroredRepository(mirror, settings);
                if (candidate != null) {
                    result.add(new MirrorCandidate(mirror.getMirrorOf(), candidate));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Cannot read Maven mirrors from Maven settings, no mirror will be applied.", e);
            return ImmutableList.of();
        }
        return result.build();
    }

    private @Nullable MirroredRepository toMirroredRepository(Mirror mirror, Settings settings) {
        String mirrorId = mirror.getId();
        try {
            URI url = new URI(mirror.getUrl());
            if (mirror.isBlocked()) {
                // A blocked mirror fails the repositories it matches; its URL is never
                // contacted and its credentials are irrelevant
                return new MirroredRepository(mirrorId, url, true, null, null);
            }
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                LOGGER.lifecycle("Maven mirror '{}' does not use HTTPS: {}", mirrorId, url);
            }
            MirrorCredentials credentials = resolveCredentials(mirrorId, settings);
            MirrorHttpHeader httpHeader = credentials == null ? resolveHttpHeader(mirrorId, settings) : null;
            return new MirroredRepository(mirrorId, url, false, credentials, httpHeader);
        } catch (URISyntaxException e) {
            LOGGER.lifecycle("Maven mirror '{}' has an invalid URL and will be ignored: {}", mirrorId, mirror.getUrl());
            return null;
        }
    }

    private static class MirrorCandidate {
        final @Nullable String mirrorOf;
        final MirroredRepository mirror;

        MirrorCandidate(@Nullable String mirrorOf, MirroredRepository mirror) {
            this.mirrorOf = mirrorOf;
            this.mirror = mirror;
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
        return startParameter.isSharedMavenSettings();
    }
}
