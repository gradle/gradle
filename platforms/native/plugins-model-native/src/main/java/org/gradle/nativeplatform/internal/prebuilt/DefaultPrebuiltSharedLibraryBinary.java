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
import org.gradle.nativeplatform.PrebuiltSharedLibraryBinary;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.File;

public class DefaultPrebuiltSharedLibraryBinary extends AbstractPrebuiltLibraryBinary implements PrebuiltSharedLibraryBinary {
    private File sharedLibraryFile;
    private File sharedLibraryLinkFile;

    public DefaultPrebuiltSharedLibraryBinary(String name, PrebuiltLibrary library, BuildType buildType, NativePlatform targetPlatform, Flavor flavor, FileCollectionFactory fileCollectionFactory) {
        super(name, library, buildType, targetPlatform, flavor, fileCollectionFactory);
    }

    @Override
    public String getDisplayName() {
        return "prebuilt shared library '" + getComponent().getName() + ":" + getName() + "'";
    }

    @Override
    public void setSharedLibraryFile(File sharedLibraryFile) {
        this.sharedLibraryFile = sharedLibraryFile;
    }

    @Override
    public File getSharedLibraryFile() {
        return sharedLibraryFile;
    }

    @Override
    public void setSharedLibraryLinkFile(File sharedLibraryLinkFile) {
        this.sharedLibraryLinkFile = sharedLibraryLinkFile;
    }

    @Override
    public File getSharedLibraryLinkFile() {
        if (sharedLibraryLinkFile != null) {
            return sharedLibraryLinkFile;
        }
        return sharedLibraryFile;
    }

    @Override
    public FileCollection getLinkFiles() {
        return createFileCollection(getSharedLibraryLinkFile(), "link files", "Shared library link files");
    }

    @Override
    public FileCollection getRuntimeFiles() {
        return createFileCollection(getSharedLibraryFile(), "runtime files", "Shared library runtime files");
    }
}
