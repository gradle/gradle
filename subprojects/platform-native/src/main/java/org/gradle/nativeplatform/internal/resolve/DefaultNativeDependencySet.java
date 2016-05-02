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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.file.FileCollection;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeLibraryBinary;

public class DefaultNativeDependencySet implements NativeDependencySet {
    private final NativeLibraryBinary binary;

    public DefaultNativeDependencySet(NativeLibraryBinary binary) {
        this.binary = binary;
    }

    @Override
    public FileCollection getIncludeRoots() {
        return binary.getHeaderDirs();
    }

    @Override
    public FileCollection getLinkFiles() {
        return binary.getLinkFiles();
    }

    @Override
    public FileCollection getRuntimeFiles() {
        return binary.getRuntimeFiles();
    }
}
