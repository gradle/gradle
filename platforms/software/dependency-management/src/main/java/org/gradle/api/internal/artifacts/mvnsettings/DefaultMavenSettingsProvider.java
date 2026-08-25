/*
 * Copyright 2013 the original author or authors.
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
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.SettingsReader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.resource.local.FileResourceListener;
import org.jspecify.annotations.Nullable;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultMavenSettingsProvider implements MavenSettingsProvider {
    private static final Logger LOGGER = Logging.getLogger(DefaultMavenSettingsProvider.class);

    /**
     * The mirror Maven ships in its own {@code conf/settings.xml} since 3.8.1, blocking every
     * external plain-http repository. It is ignored here: Gradle already refuses http repositories
     * unless the build opts in with {@code allowInsecureProtocol}, so honouring Maven's
     * installation-wide default would add no protection and only override that explicit opt-in —
     * and only on machines where {@code M2_HOME} happens to be set. Mirrors the user declares
     * blocked themselves are still honoured.
     */
    private static final String MAVEN_DEFAULT_HTTP_BLOCKER_ID = "maven-default-http-blocker";

    private final MavenFileLocations mavenFileLocations;
    private final FileResourceListener fileResourceListener;

    private @Nullable List<MirroredRepository> mirrors;

    public DefaultMavenSettingsProvider(MavenFileLocations mavenFileLocations, FileResourceListener fileResourceListener) {
        this.mavenFileLocations = mavenFileLocations;
        this.fileResourceListener = fileResourceListener;
    }

    /**
     * Builds a complete {@code Settings} instance for this machine, merging the user and global
     * settings.xml through Maven's own {@code DefaultSettingsBuilder}.
     *
     * <p>Expensive: it reads and parses both XML files and interpolates system properties into
     * them, on every call. No external process is involved, despite what this javadoc used to say.
     */
    @Override
    public Settings buildSettings() throws SettingsBuildingException {
        observeSettingsFiles();
        // settings-security.xml is deliberately not declared here: the settings builder never
        // reads it, it only leaves {...} passwords encrypted. Decryption is what reads it, and
        // that only happens while building the mirror list.
        DefaultSettingsBuilderFactory factory = new DefaultSettingsBuilderFactory();
        DefaultSettingsBuilder defaultSettingsBuilder = factory.newInstance();
        DefaultSettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();
        settingsBuildingRequest.setSystemProperties(System.getProperties());
        settingsBuildingRequest.setUserSettingsFile(mavenFileLocations.getUserSettingsFile());
        settingsBuildingRequest.setGlobalSettingsFile(mavenFileLocations.getGlobalSettingsFile());
        SettingsBuildingResult settingsBuildingResult = defaultSettingsBuilder.build(settingsBuildingRequest);
        return settingsBuildingResult.getEffectiveSettings();
    }

    /**
     * Read the local repository location from local Maven settings files.
     *
     * <p>Deliberately does <em>not</em> declare the settings files as a build input. Unlike
     * {@link #buildSettings()} this runs on every build that resolves anything, through
     * {@code LocallyAvailableResourceFinderFactory}, so fingerprinting here would make every
     * Gradle build sensitive to settings.xml. Tracking it is a real gap, but a separate change.
     *
     * @return The path to the local repository, or <code>null</code> if not specified in Maven settings.
     */
    @Override
    public String getLocalRepository() {
        String localRepo = readLocalRepository(mavenFileLocations.getUserSettingsFile());
        if (localRepo == null) {
            localRepo = readLocalRepository(mavenFileLocations.getGlobalSettingsFile());
        }
        return localRepo;
    }

    @Override
    public synchronized List<MirroredRepository> getMirrors() {
        if (mirrors == null) {
            mirrors = computeMirrors();
        }
        return mirrors;
    }

    private List<MirroredRepository> computeMirrors() {
        // Any {...} password in the settings is decrypted against the master password here, so
        // this path is sensitive to settings-security.xml where buildSettings() alone is not
        observe(mavenFileLocations.getUserSecuritySettingsFile());
        ImmutableList.Builder<MirroredRepository> result = ImmutableList.builder();
        try {
            Settings settings = buildSettings();
            for (Mirror mirror : settings.getMirrors()) {
                if (MAVEN_DEFAULT_HTTP_BLOCKER_ID.equals(mirror.getId())) {
                    continue;
                }
                MirroredRepository candidate = toMirroredRepository(mirror, settings);
                if (candidate != null) {
                    result.add(candidate);
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
        String mirrorOf = mirror.getMirrorOf();
        try {
            URI url = new URI(mirror.getUrl());
            if (mirror.isBlocked()) {
                // A blocked mirror fails the repositories it matches; its URL is never
                // contacted and its credentials are irrelevant
                return new MirroredRepository(mirrorOf, mirrorId, url, true, null, null);
            }
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                LOGGER.info("Maven mirror '{}' does not use HTTPS: {}", mirrorId, url);
            }
            MirrorCredentials credentials = resolveCredentials(mirrorId, settings);
            MirrorHttpHeader httpHeader = credentials == null ? resolveHttpHeader(mirrorId, settings) : null;
            return new MirroredRepository(mirrorOf, mirrorId, url, false, credentials, httpHeader);
        } catch (URISyntaxException e) {
            LOGGER.warn("Maven mirror '{}' has an invalid URL and will be ignored: {}", mirrorId, mirror.getUrl());
            return null;
        }
    }

    /**
     * Resolves the username/password credentials for a mirror from the settings.xml
     * {@code <server>} entry matching the mirror id, whose password may be encrypted with the
     * Maven master password.
     */
    private @Nullable MirrorCredentials resolveCredentials(String mirrorId, Settings settings) {
        Server server = settings.getServer(mirrorId);
        if (server == null || (server.getUsername() == null && server.getPassword() == null)) {
            return null;
        }
        try {
            String decryptedPassword = decryptPassword(server.getPassword());
            LOGGER.info("Using credentials from the Maven settings server entry '{}' for Maven mirror '{}'.", server.getId(), mirrorId);
            return new MirrorCredentials(server.getUsername(), decryptedPassword);
        } catch (Exception e) {
            LOGGER.warn("Cannot decrypt the password of the Maven settings server entry '{}', continuing without credentials for Maven mirror '{}'. ({})",
                server.getId(), mirrorId, e.getMessage());
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
            LOGGER.warn("Ignoring malformed httpHeaders configuration of the Maven settings server entry '{}' for Maven mirror '{}': each property needs a name and a value.", server.getId(), mirrorId);
            return null;
        }
        if (properties.length > 1) {
            LOGGER.warn("Only the first HTTP header ('{}') of the Maven settings server entry '{}' is applied to Maven mirror '{}'; {} additional header(s) are ignored.", name, server.getId(), mirrorId, properties.length - 1);
        }
        try {
            String decryptedValue = decryptPassword(value);
            LOGGER.info("Using HTTP header '{}' from the Maven settings server entry '{}' for Maven mirror '{}'.", name, server.getId(), mirrorId);
            return new MirrorHttpHeader(name, decryptedValue);
        } catch (Exception e) {
            LOGGER.warn("Cannot decrypt the value of HTTP header '{}' of the Maven settings server entry '{}', continuing without credentials for Maven mirror '{}'. ({})", name, server.getId(), mirrorId, e.getMessage());
            return null;
        }
    }

    private static @Nullable String childValue(Xpp3Dom parent, String childName) {
        Xpp3Dom child = parent.getChild(childName);
        return child == null ? null : child.getValue();
    }

    /**
     * Decrypts a Maven-encrypted ({@code {...}}-wrapped) password using the same mechanism as
     * Maven itself: the master password from {@code ~/.m2/settings-security.xml}. Plaintext values
     * pass through untouched, without reading settings-security.xml at all.
     *
     * <p>Only the default location is supported. plexus-sec-dispatcher still lets its own
     * {@code settings.security} system property redirect the read, and a build that sets it
     * decrypts against a file this prototype does not declare as a configuration cache input.
     */
    private @Nullable String decryptPassword(@Nullable String password) throws Exception {
        if (password == null) {
            return null;
        }
        DefaultSecDispatcher secDispatcher = new DefaultSecDispatcher(new DefaultPlexusCipher());
        secDispatcher.setConfigurationFile(mavenFileLocations.getUserSecuritySettingsFile().getAbsolutePath());
        return secDispatcher.decrypt(password);
    }

    /**
     * Registers the settings.xml files as build inputs. Both are declared even when absent:
     * creating one has to invalidate the configuration cache just as editing one does, and a
     * missing file fingerprints as missing rather than not at all.
     */
    private void observeSettingsFiles() {
        observe(mavenFileLocations.getUserSettingsFile());
        observe(mavenFileLocations.getGlobalSettingsFile());
    }

    private void observe(@Nullable File settingsFile) {
        if (settingsFile != null) {
            fileResourceListener.fileObserved(settingsFile);
        }
    }

    private @Nullable String readLocalRepository(@Nullable File settingsFile) {
        if (settingsFile == null || !settingsFile.exists()) {
            return null;
        }
        Map<String, ?> options = Collections.singletonMap(SettingsReader.IS_STRICT, Boolean.FALSE);
        SettingsReader settingsReader = new DefaultSettingsReader();
        try {
            String localRepository = settingsReader.read(settingsFile, options).getLocalRepository();
            return StringUtils.isEmpty(localRepository) ? null : localRepository;
        } catch (Exception parseException) {
            throw new CannotLocateLocalMavenRepositoryException("Unable to parse local Maven settings: " + settingsFile.getAbsolutePath(), parseException);
        }
    }
}
