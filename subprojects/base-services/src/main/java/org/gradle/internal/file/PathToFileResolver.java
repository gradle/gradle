/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.file;

import java.io.File;

/**
 * Resolves some path object to a `File`. May or may not be able to resolve relative paths.
 */
public interface PathToFileResolver {
    /**
     * Resolves the given path to a file.
     */
    File resolve(Object path);

    /**
     * Returns a resolver that resolves paths relative to the given base dir.
     */
    PathToFileResolver newResolver(File baseDir);

    /**
     * Indicates if this resolver is able to resolved relative paths.
     *
     * @return {@code true} if it can resolve relative path, {@code false} otherwise.
     */
    boolean canResolveRelativePath();
}
