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
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.PrebuiltStaticLibraryBinary;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.File;

public class DefaultPrebuiltStaticLibraryBinary extends AbstractPrebuiltLibraryBinary implements PrebuiltStaticLibraryBinary {
    private File staticLibraryFile;

    public DefaultPrebuiltStaticLibraryBinary(String name, PrebuiltLibrary library, BuildType buildType, NativePlatform targetPlatform, Flavor flavor) {
        super(name, library, buildType, targetPlatform, flavor);
    }

    public String getDisplayName() {
        return String.format("static library '%s'", getName());
    }

    public void setStaticLibraryFile(File staticLibraryFile) {
        this.staticLibraryFile = staticLibraryFile;
    }

    public File getStaticLibraryFile() {
        return staticLibraryFile;
    }

    public FileCollection getLinkFiles() {
        return createFileCollection(getStaticLibraryFile(), "Static library file");
    }

    public FileCollection getRuntimeFiles() {
        return new SimpleFileCollection();
    }
}
