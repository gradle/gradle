/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.file;

import org.gradle.api.Project;
import org.gradle.api.resources.ReadableResource;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;


/**
 * Operations on archives such as ZIP or TAR files.
 *
 * <p>An instance of this type can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@code javax.inject.Inject}.
 *
 * @since 6.6
 */
@ServiceScope(Scopes.Build.class)
public interface ArchiveOperations {

    /**
     * Creates resource that points to a gzip compressed file at the given path.
     * The path is evaluated as per {@link Project#file(Object)}.
     *
     * @param path The path evaluated as per {@link Project#file(Object)}.
     * @since 7.0
     */
    ReadableResource gzip(Object path);

    /**
     * Creates resource that points to a bzip2 compressed file at the given path.
     * The path is evaluated as per {@link Project#file(Object)}.
     *
     * @param path The path evaluated as per {@link Project#file(Object)}.
     * @since 7.0
     */
    ReadableResource bzip2(Object path);

    /**
     * <p>Creates a read-only {@code FileTree} which contains the contents of the given ZIP file, as defined by {@link Project#zipTree(Object)}.</p>
     *
     * @param zipPath The ZIP file. Evaluated as per {@link Project#file(Object)}.
     * @return the file tree. Never returns null.
     */
    FileTree zipTree(Object zipPath);

    /**
     * <p>Creates a read-only {@code FileTree} which contains the contents of the given TAR file, as defined by {@link Project#tarTree(Object)}.</p>
     *
     * @param tarPath The TAR file. Evaluated as per {@link Project#file(Object)}.
     * @return the file tree. Never returns null.
     */
    FileTree tarTree(Object tarPath);
}
