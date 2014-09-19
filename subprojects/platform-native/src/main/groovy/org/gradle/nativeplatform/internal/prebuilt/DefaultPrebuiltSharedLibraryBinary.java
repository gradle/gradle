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
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.PrebuiltSharedLibraryBinary;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.File;

public class DefaultPrebuiltSharedLibraryBinary extends AbstractPrebuiltLibraryBinary implements PrebuiltSharedLibraryBinary {
    private File sharedLibraryFile;
    private File sharedLibraryLinkFile;

    public DefaultPrebuiltSharedLibraryBinary(String name, PrebuiltLibrary library, BuildType buildType, NativePlatform targetPlatform, Flavor flavor) {
        super(name, library, buildType, targetPlatform, flavor);
    }

    public String getDisplayName() {
        return String.format("shared library '%s'", getName());
    }

    public void setSharedLibraryFile(File sharedLibraryFile) {
        this.sharedLibraryFile = sharedLibraryFile;
    }

    public File getSharedLibraryFile() {
        return sharedLibraryFile;
    }

    public void setSharedLibraryLinkFile(File sharedLibraryLinkFile) {
        this.sharedLibraryLinkFile = sharedLibraryLinkFile;
    }

    public File getSharedLibraryLinkFile() {
        if (sharedLibraryLinkFile != null) {
            return sharedLibraryLinkFile;
        }
        return sharedLibraryFile;
    }

    public FileCollection getLinkFiles() {
        return createFileCollection(getSharedLibraryLinkFile(), "Shared library link file");
    }

    public FileCollection getRuntimeFiles() {
        return createFileCollection(getSharedLibraryFile(), "Shared library runtime file");
    }
}
