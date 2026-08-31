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

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;

/**
 * Reads the local Maven settings files. This is the single place that parses them, so
 * everything derived from settings.xml — the local repository location, the mirrors — is
 * produced here rather than by each consumer parsing for itself.
 */
@NullMarked
@ServiceScope(Scope.BuildTree.class)
public interface MavenSettingsProvider {
    /**
     * Builds the effective settings for this machine, merging the user and global settings.xml.
     *
     * <p>Expensive, and <em>not</em> cached: every call re-reads and re-parses both XML files
     * from disk. Callers that need the settings more than once should hold on to the result
     * rather than calling this again. Contrast {@link #getMirrors()}, which parses once and
     * caches for the life of the service.
     */
    Settings buildSettings() throws SettingsBuildingException;

    /**
     * Reads the local repository location declared in the settings.
     *
     * <p>Expensive, and <em>not</em> cached: every call re-reads and re-parses the user
     * settings.xml, and the global one as well when the user one declares no location. This
     * runs on every build that resolves anything, so callers on a hot path should cache it.
     *
     * @return the configured local repository path, or null when neither settings file declares one
     */
    @Nullable
    String getLocalRepository();

    /**
     * The mirrors declared in the settings, in declaration order, with their credentials
     * already resolved and decrypted. Parsed once and cached for the life of the service.
     *
     * <p>Reading the settings registers them as a build input, so callers must only ask for
     * the mirrors when the feature that consumes them is actually enabled.
     */
    List<MirroredRepository> getMirrors();

    /**
     * A mirror declared in settings.xml: the pattern it matches, the mirror id and URL, and
     * the credentials to use for it, if any.
     */
    final class MirroredRepository {
        private final @Nullable String mirrorOf;
        private final String id;
        private final URI url;
        private final boolean blocked;
        private final @Nullable MirrorCredentials credentials;
        private final @Nullable MirrorHttpHeader httpHeader;

        public MirroredRepository(@Nullable String mirrorOf, String id, URI url, boolean blocked, @Nullable MirrorCredentials credentials, @Nullable MirrorHttpHeader httpHeader) {
            this.mirrorOf = mirrorOf;
            this.id = id;
            this.url = url;
            this.blocked = blocked;
            this.credentials = credentials;
            this.httpHeader = httpHeader;
        }

        /**
         * The raw {@code <mirrorOf>} pattern. Matching it against a repository is
         * {@link MavenMirrorResolver}'s job, not this type's.
         */
        public @Nullable String getMirrorOf() {
            return mirrorOf;
        }

        public String getId() {
            return id;
        }

        public URI getUrl() {
            return url;
        }

        /**
         * Whether the mirror blocks the repositories it matches ({@code <blocked>true</blocked>},
         * Maven 3.8+). Resolution against a blocked repository must fail rather than contact
         * either the original URL or the mirror URL.
         */
        public boolean isBlocked() {
            return blocked;
        }

        /**
         * The credentials the mirror itself requires, from the settings.xml {@code <server>}
         * entry matching the mirror id, with the password decrypted. Null when no such entry
         * is configured, or when the mirror authenticates with an HTTP header instead.
         */
        public @Nullable MirrorCredentials getCredentials() {
            return credentials;
        }

        /**
         * The HTTP header the mirror authenticates with, from the
         * {@code <configuration><httpHeaders>} block of the settings.xml {@code <server>}
         * entry matching the mirror id. Null when not configured, or when username/password
         * credentials apply instead. At most one of credentials and http header is set.
         */
        public @Nullable MirrorHttpHeader getHttpHeader() {
            return httpHeader;
        }
    }

    /**
     * An HTTP header used to authenticate against a mirror.
     */
    record MirrorHttpHeader(String name, String value) {
        /**
         * Overridden because the value is a secret and the generated {@code toString()} would
         * print it. Follows {@code DefaultHttpHeaderCredentials}: name the header, never the value.
         */
        @Override
        public String toString() {
            return String.format("Maven mirror credentials [header: %s]", name);
        }
    }

    /**
     * Username and password for a mirror. Either may be absent: Maven allows a {@code <server>}
     * entry to declare only one of them.
     */
    record MirrorCredentials(@Nullable String username, @Nullable String password) {
        /**
         * Overridden because the password is a secret and the generated {@code toString()} would
         * print it. Follows {@code DefaultPasswordCredentials}: name the user, never the password.
         */
        @Override
        public String toString() {
            return String.format("Maven mirror credentials [username: %s]", username);
        }
    }
}
