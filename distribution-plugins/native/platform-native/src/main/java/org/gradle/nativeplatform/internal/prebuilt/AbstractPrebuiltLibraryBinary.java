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

package org.gradle.nativeplatform.internal.prebuilt;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public abstract class AbstractPrebuiltLibraryBinary implements NativeLibraryBinary {
    private final String name;
    private final PrebuiltLibrary library;
    private final BuildType buildType;
    private final NativePlatform targetPlatform;
    private final Flavor flavor;
    protected final FileCollectionFactory fileCollectionFactory;

    public AbstractPrebuiltLibraryBinary(String name, PrebuiltLibrary library, BuildType buildType, NativePlatform targetPlatform, Flavor flavor, FileCollectionFactory fileCollectionFactory) {
        this.name = name;
        this.library = library;
        this.buildType = buildType;
        this.targetPlatform = targetPlatform;
        this.flavor = flavor;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getName() {
        return name;
    }

    public PrebuiltLibrary getComponent() {
        return library;
    }

    @Override
    public BuildType getBuildType() {
        return buildType;
    }

    @Override
    public Flavor getFlavor() {
        return flavor;
    }

    @Override
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    @Override
    public FileCollection getHeaderDirs() {
        return library.getHeaders().getSourceDirectories();
    }

    protected FileCollection createFileCollection(File file, String fileCollectionDisplayName, String fileDescription) {
        return fileCollectionFactory.create(new ValidatingFileSet(file, fileCollectionDisplayName, fileDescription));
    }

    private class ValidatingFileSet implements MinimalFileSet {
        private final File file;
        private final String fileCollectionDisplayName;
        private final String fileDescription;

        private ValidatingFileSet(File file, String fileCollectionDisplayName, String fileDescription) {
            this.file = file;
            this.fileCollectionDisplayName = fileCollectionDisplayName;
            this.fileDescription = fileDescription;
        }

        @Override
        public String getDisplayName() {
            return fileCollectionDisplayName + " for " + AbstractPrebuiltLibraryBinary.this.getDisplayName();
        }

        @Override
        public Set<File> getFiles() {
            if (file == null) {
                throw new PrebuiltLibraryResolveException(String.format("%s not set for %s.", fileDescription, AbstractPrebuiltLibraryBinary.this.getDisplayName()));
            }
            if (!file.exists() || !file.isFile()) {
                throw new PrebuiltLibraryResolveException(String.format("%s %s does not exist for %s.", fileDescription, file.getAbsolutePath(), AbstractPrebuiltLibraryBinary.this.getDisplayName()));
            }
            return Collections.singleton(file);
        }
    }
}
