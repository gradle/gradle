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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Optional;

/**
 * Resolves the mirror to use for a remote Maven repository URL, based on the
 * mirrors declared in the local Maven {@code settings.xml} files.
 *
 * <p>Prototype: only enabled when the {@code org.gradle.internal.mavenMirrors}
 * Gradle property is set to {@code true}.
 */
@ServiceScope(Scope.Build.class)
public interface MavenMirrorResolver {

    /**
     * Returns the mirror that should replace the given repository URL, if any.
     *
     * <p>Mirrors are matched in settings declaration order against the repository's
     * effective id — {@code central} when the URL is Maven Central's, the Gradle
     * repository name otherwise — using the Maven {@code mirrorOf} grammar; the first
     * match wins. Empty when the feature is disabled, no mirror matches, the URL is
     * not a remote ({@code http}/{@code https}) URL, or the URL is already the mirror
     * URL. A returned mirror may be {@link MirroredRepository#isBlocked() blocked},
     * in which case the repository must not be used at all.
     */
    Optional<MirroredRepository> mirrorFor(URI original, String repositoryName);

    /**
     * A mirror selected for a repository: the mirror id from settings.xml, its URL,
     * and the credentials to use for it, if any.
     */
    final class MirroredRepository {
        private final String id;
        private final URI url;
        private final boolean blocked;
        private final @Nullable MirrorCredentials credentials;
        private final @Nullable MirrorHttpHeader httpHeader;

        public MirroredRepository(String id, URI url, boolean blocked, @Nullable MirrorCredentials credentials, @Nullable MirrorHttpHeader httpHeader) {
            this.id = id;
            this.url = url;
            this.blocked = blocked;
            this.credentials = credentials;
            this.httpHeader = httpHeader;
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
         * The credentials the mirror itself requires: either the settings.xml
         * {@code <server>} entry matching the mirror id (with the password decrypted),
         * or the {@code <mirrorId>Username}/{@code <mirrorId>Password} Gradle property
         * override. Null when neither is configured, or when the mirror authenticates
         * with an HTTP header instead.
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
    final class MirrorHttpHeader {
        private final String name;
        private final String value;

        public MirrorHttpHeader(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Username and password for a mirror.
     */
    final class MirrorCredentials {
        private final @Nullable String username;
        private final @Nullable String password;

        public MirrorCredentials(@Nullable String username, @Nullable String password) {
            this.username = username;
            this.password = password;
        }

        public @Nullable String getUsername() {
            return username;
        }

        public @Nullable String getPassword() {
            return password;
        }
    }
}
