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
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public abstract class AbstractPrebuiltLibraryBinary extends AbstractBuildableModelElement implements NativeLibraryBinary {
    private final String name;
    private final PrebuiltLibrary library;
    private final BuildType buildType;
    private final NativePlatform targetPlatform;
    private final Flavor flavor;

    public AbstractPrebuiltLibraryBinary(String name, PrebuiltLibrary library, BuildType buildType, NativePlatform targetPlatform, Flavor flavor) {
        this.name = name;
        this.library = library;
        this.buildType = buildType;
        this.targetPlatform = targetPlatform;
        this.flavor = flavor;
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

    public BuildType getBuildType() {
        return buildType;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public FileCollection getHeaderDirs() {
        return new SimpleFileCollection(library.getHeaders().getSrcDirs());
    }

    protected FileCollection createFileCollection(File file, String fileDescription) {
        return new FileCollectionAdapter(new ValidatingFileSet(file, getComponent().getName(), fileDescription));
    }

    private static class ValidatingFileSet implements MinimalFileSet {
        private final File file;
        private final String libraryName;
        private final String fileDescription;

        private ValidatingFileSet(File file, String libraryName, String fileDescription) {
            this.file = file;
            this.libraryName = libraryName;
            this.fileDescription = fileDescription;
        }

        public String getDisplayName() {
            return String.format("%s for prebuilt library '%s'", fileDescription, libraryName);
        }

        public Set<File> getFiles() {
            if (file == null) {
                throw new PrebuiltLibraryResolveException(String.format("%s not set for prebuilt library '%s'.", fileDescription, libraryName));
            }
            if (!file.exists() || !file.isFile()) {
                throw new PrebuiltLibraryResolveException(String.format("%s %s does not exist for prebuilt library '%s'.", fileDescription, file.getAbsolutePath(), libraryName));
            }
            return Collections.singleton(file);
        }
    }
}
