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

package org.gradle.plugins.binaries.model.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.plugins.binaries.model.Library;
import org.gradle.plugins.binaries.model.LibraryCompileSpec;
import org.gradle.plugins.binaries.model.SharedLibraryBinary;
import org.gradle.plugins.binaries.model.SourceSet;

public class DefaultSharedLibraryBinary implements SharedLibraryBinary {
    private final Library library;

    public DefaultSharedLibraryBinary(Library library) {
        this.library = library;
    }

    public SourceDirectorySet getHeaders() {
        return library.getHeaders();
    }

    public LibraryCompileSpec getSpec() {
        return library.getSpec();
    }

    public DomainObjectSet<SourceSet> getSourceSets() {
        return library.getSourceSets();
    }

    public String getName() {
        return library.getName();
    }
}
