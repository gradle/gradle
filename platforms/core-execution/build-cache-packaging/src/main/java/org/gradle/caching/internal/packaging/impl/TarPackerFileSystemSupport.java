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

package org.gradle.caching.internal.packaging.impl;

import org.gradle.internal.file.TreeType;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.io.IOException;

@ServiceScope(Scope.Build.class)
public interface TarPackerFileSystemSupport {
    /**
     * Make sure the parent directory exists while the given file itself doesn't exist.
     */
    void ensureFileIsMissing(File entry) throws IOException;

    /**
     * Make sure directory exists.
     */
    void ensureDirectoryForTree(TreeType type, File root) throws IOException;
}
