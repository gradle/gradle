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

package org.gradle.nativebinaries.internal.prebuilt;

import org.gradle.api.file.FileCollection;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.PrebuiltLibrary;
import org.gradle.nativebinaries.SharedLibraryBinary;
import org.gradle.nativebinaries.platform.Platform;

import java.io.File;

public class DefaultPrebuiltSharedLibraryBinary extends AbstractPrebuiltLibraryBinary implements SharedLibraryBinary {
    private File sharedLibraryFile;
    private File sharedLibraryLinkFile;

    public DefaultPrebuiltSharedLibraryBinary(String name, PrebuiltLibrary library, BuildType buildType, Platform targetPlatform, Flavor flavor) {
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
