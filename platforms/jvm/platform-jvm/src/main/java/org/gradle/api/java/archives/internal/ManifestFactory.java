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

package org.gradle.api.java.archives.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Factory for creating manifests that allows a new manifest to be created or a manifest to be read from file.
 */
@ServiceScope(Scope.Project.class)
public interface ManifestFactory {
    /**
     * Creates a new manifest using the default character set defined by {@link ManifestInternal#DEFAULT_CONTENT_CHARSET}
     * and initializes it.
     */
    DefaultManifest create();

    /**
     * Creates a new manifest using the given character set and initializes it.
     *
     * @param contentCharset the character set to use for the manifest content
     */
    DefaultManifest create(Provider<String> contentCharset);

    /**
     * Reads a manifest from the given path with the given character set.
     *
     * @param path the path to the manifest file resolved using {@link FileResolver#resolve(Object)}
     * @param contentCharset the character set to use for the manifest content
     */
    DefaultManifest readFrom(Object path, Provider<String> contentCharset);
}
