/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.internal.file.PathToFileResolver;

import java.io.File;

public class DefaultFileLookup implements FileLookup {
    private final IdentityFileResolver fileResolver = new IdentityFileResolver();

    @Override
    public FileResolver getFileResolver() {
        return fileResolver;
    }

    @Override
    public PathToFileResolver getPathToFileResolver() {
        return getFileResolver();
    }

    @Override
    public FileResolver getFileResolver(File baseDirectory) {
        return fileResolver.withBaseDir(baseDirectory);
    }

    @Override
    public PathToFileResolver getPathToFileResolver(File baseDirectory) {
        return getFileResolver(baseDirectory);
    }
}
