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
 * Gradle property is set to {@code true}, and only {@code <mirrorOf>*</mirrorOf>}
 * mirrors are honored.
 */
@ServiceScope(Scope.Build.class)
public interface MavenMirrorResolver {

    /**
     * Returns the mirror that should replace the given repository URL, if any.
     *
     * <p>Empty when the feature is disabled, no wildcard mirror is configured,
     * the URL is not a remote ({@code http}/{@code https}) URL, or the URL is
     * already the mirror URL.
     */
    Optional<MirroredRepository> mirrorFor(URI original);

    /**
     * A mirror selected for a repository: the mirror id from settings.xml, its URL,
     * and the credentials to use for it, if any.
     */
    final class MirroredRepository {
        private final String id;
        private final URI url;
        private final @Nullable MirrorCredentials credentials;

        public MirroredRepository(String id, URI url, @Nullable MirrorCredentials credentials) {
            this.id = id;
            this.url = url;
            this.credentials = credentials;
        }

        public String getId() {
            return id;
        }

        public URI getUrl() {
            return url;
        }

        /**
         * The credentials the mirror itself requires: either the settings.xml
         * {@code <server>} entry matching the mirror id (with the password decrypted),
         * or the {@code <mirrorId>Username}/{@code <mirrorId>Password} Gradle property
         * override. Null when neither is configured.
         */
        public @Nullable MirrorCredentials getCredentials() {
            return credentials;
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
