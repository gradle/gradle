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
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.PrebuiltStaticLibraryBinary;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.File;

public class DefaultPrebuiltStaticLibraryBinary extends AbstractPrebuiltLibraryBinary implements PrebuiltStaticLibraryBinary {
    private File staticLibraryFile;

    public DefaultPrebuiltStaticLibraryBinary(String name, PrebuiltLibrary library, BuildType buildType, NativePlatform targetPlatform, Flavor flavor, FileCollectionFactory fileCollectionFactory) {
        super(name, library, buildType, targetPlatform, flavor, fileCollectionFactory);
    }

    @Override
    public String getDisplayName() {
        return "prebuilt static library '" + getComponent().getName() + ":" + getName() + "'";
    }

    @Override
    public void setStaticLibraryFile(File staticLibraryFile) {
        this.staticLibraryFile = staticLibraryFile;
    }

    @Override
    public File getStaticLibraryFile() {
        return staticLibraryFile;
    }

    @Override
    public FileCollection getLinkFiles() {
        return createFileCollection(getStaticLibraryFile(), "link files", "Static library file");
    }

    @Override
    public FileCollection getRuntimeFiles() {
        return fileCollectionFactory.empty("runtime files for " + getDisplayName());
    }
}
