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

package org.gradle.nativeplatform.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.nativeplatform.StaticLibraryBinary;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultStaticLibraryBinarySpec extends AbstractNativeLibraryBinarySpec implements StaticLibraryBinary, StaticLibraryBinarySpecInternal {
    private final List<FileCollection> additionalLinkFiles = new ArrayList<FileCollection>();
    private final DefaultTasksCollection tasks = new DefaultTasksCollection(super.getTasks());
    private File staticLibraryFile;

    @Override
    public File getStaticLibraryFile() {
        return staticLibraryFile;
    }

    @Override
    public void setStaticLibraryFile(File staticLibraryFile) {
        this.staticLibraryFile = staticLibraryFile;
    }

    @Override
    public File getPrimaryOutput() {
        return getStaticLibraryFile();
    }

    @Override
    public void additionalLinkFiles(FileCollection files) {
        this.additionalLinkFiles.add(files);
    }

    @Override
    public FileCollection getLinkFiles() {
        return getFileCollectionFactory().create(new StaticLibraryLinkOutputs());
    }

    @Override
    public FileCollection getRuntimeFiles() {
        return getFileCollectionFactory().empty("Runtime files for " + getDisplayName());
    }

    @Override
    protected ObjectFilesToBinary getCreateOrLink() {
        return tasks.getCreateStaticLib();
    }

    @Override
    public StaticLibraryBinarySpec.TasksCollection getTasks() {
        return tasks;
    }

    static class DefaultTasksCollection extends BinaryTasksCollectionWrapper implements StaticLibraryBinarySpec.TasksCollection {
        public DefaultTasksCollection(BinaryTasksCollection delegate) {
            super(delegate);
        }

        @Override
        public CreateStaticLibrary getCreateStaticLib() {
            return findSingleTaskWithType(CreateStaticLibrary.class);
        }
    }

    private class StaticLibraryLinkOutputs extends LibraryOutputs {
        @Override
        public String getDisplayName() {
            return "Link files for " + DefaultStaticLibraryBinarySpec.this.getDisplayName();
        }

        @Override
        protected boolean hasOutputs() {
            return hasSources() || !additionalLinkFiles.isEmpty();
        }

        @Override
        protected Set<File> getOutputs() {
            Set<File> allFiles = new LinkedHashSet<File>();
            if (hasSources()) {
                allFiles.add(getStaticLibraryFile());
            }
            for (FileCollection resourceSet : additionalLinkFiles) {
                allFiles.addAll(resourceSet.getFiles());
            }
            return allFiles;
        }
    }
}
