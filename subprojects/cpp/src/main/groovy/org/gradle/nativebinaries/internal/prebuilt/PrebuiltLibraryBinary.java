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

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.LibraryBinaryInternal;
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.ToolChain;

import java.io.File;
import java.util.Collection;

public abstract class PrebuiltLibraryBinary implements LibraryBinaryInternal {
    private final BinaryNamingScheme namingScheme;
    private final PrebuiltLibrary library;
    private final BuildType buildType;
    private final Platform targetPlatform;
    private final Flavor flavor;

    private File outputFile;

    public PrebuiltLibraryBinary(BinaryNamingScheme namingScheme, PrebuiltLibrary library, BuildType buildType, Platform targetPlatform, Flavor flavor) {
        this.namingScheme = namingScheme;
        this.library = library;
        this.buildType = buildType;
        this.targetPlatform = targetPlatform;
        this.flavor = flavor;
    }

    @Override
    public String toString() {
        return namingScheme.getDescription();
    }

    public String getName() {
        return namingScheme.getLifecycleTaskName();
    }

    public PrebuiltLibrary getComponent() {
        return library;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public BuildType getBuildType() {
        return buildType;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public Platform getTargetPlatform() {
        return targetPlatform;
    }

    public LibraryNativeDependencySet resolve() {
        return new LibraryNativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return new SimpleFileCollection(library.getHeaders().getSrcDirs());
            }

            public FileCollection getLinkFiles() {
                return new SimpleFileCollection(PrebuiltLibraryBinary.this.getLinkFiles());
            }

            public FileCollection getRuntimeFiles() {
                return new SimpleFileCollection(PrebuiltLibraryBinary.this.getRuntimeFiles());
            }

            public LibraryBinary getLibraryBinary() {
                return PrebuiltLibraryBinary.this;
            }
        };
    }

    protected abstract Collection<File> getLinkFiles();
    protected abstract Collection<File> getRuntimeFiles();

    // TODO:DAZ Implement and test these
    public Collection<NativeDependencySet> getLibs() {
        return null;
    }

    public void lib(Object library) {
    }

    // TODO:DAZ The rest of this isn't requires for prebuilt binaries
    public Tool getLinker() {
        throw new UnsupportedOperationException();
    }

    public NativeBinaryTasks getTasks() {
        throw new UnsupportedOperationException();
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        throw new UnsupportedOperationException();
    }

    public void source(Object source) {
        throw new UnsupportedOperationException();
    }

    public ToolChain getToolChain() {
        throw new UnsupportedOperationException();
    }

    public boolean isBuildable() {
        throw new UnsupportedOperationException();
    }

    public void setLifecycleTask(Task lifecycleTask) {
        throw new UnsupportedOperationException();
    }

    public void builtBy(Object... tasks) {
        throw new UnsupportedOperationException();
    }

    public TaskDependency getBuildDependencies() {
        throw new UnsupportedOperationException();
    }
}
