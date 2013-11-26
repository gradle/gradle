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
package org.gradle.nativebinaries.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.*;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class DefaultLibraryBinary extends DefaultNativeBinary implements LibraryBinary {
    private final Library library;

    protected DefaultLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform targetPlatform, BuildType buildType, DefaultBinaryNamingScheme namingScheme) {
        super(library, flavor, toolChain, targetPlatform, buildType, namingScheme);
        this.library = library;
    }

    public Library getComponent() {
        return library;
    }

    protected FileCollection getHeaderDirs() {
        return new FileCollectionAdapter(new MinimalFileSet() {
            public String getDisplayName() {
                return String.format("Headers for %s", getName());
            }

            public Set<File> getFiles() {
                Set<File> headerDirs = new LinkedHashSet<File>();
                for (HeaderExportingSourceSet sourceSet : getSource().withType(HeaderExportingSourceSet.class)) {
                    headerDirs.addAll(sourceSet.getExportedHeaders().getSrcDirs());
                }
                return headerDirs;
            }
        });
    }
}
