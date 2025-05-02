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
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.NativeResourceSet;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultSharedLibraryBinarySpec extends AbstractNativeLibraryBinarySpec implements SharedLibraryBinary, SharedLibraryBinarySpecInternal {
    private final DefaultTasksCollection tasks = new DefaultTasksCollection(super.getTasks());
    private File sharedLibraryFile;
    private File sharedLibraryLinkFile;

    @Override
    public File getSharedLibraryFile() {
        return sharedLibraryFile;
    }

    @Override
    public void setSharedLibraryFile(File sharedLibraryFile) {
        this.sharedLibraryFile = sharedLibraryFile;
    }

    @Override
    public File getSharedLibraryLinkFile() {
        return sharedLibraryLinkFile;
    }

    @Override
    public void setSharedLibraryLinkFile(File sharedLibraryLinkFile) {
        this.sharedLibraryLinkFile = sharedLibraryLinkFile;
    }

    @Override
    public File getPrimaryOutput() {
        return getSharedLibraryFile();
    }

    @Override
    public FileCollection getLinkFiles() {
        return getFileCollectionFactory().create(new SharedLibraryLinkOutputs());
    }

    @Override
    public FileCollection getRuntimeFiles() {
        return getFileCollectionFactory().create(new SharedLibraryRuntimeOutputs());
    }

    @Override
    protected ObjectFilesToBinary getCreateOrLink() {
        return tasks.getLink();
    }

    @Override
    public SharedLibraryBinarySpec.TasksCollection getTasks() {
        return tasks;
    }

    private static class DefaultTasksCollection extends BinaryTasksCollectionWrapper implements SharedLibraryBinarySpec.TasksCollection {
        public DefaultTasksCollection(BinaryTasksCollection delegate) {
            super(delegate);
        }

        @Override
        public LinkSharedLibrary getLink() {
            return findSingleTaskWithType(LinkSharedLibrary.class);
        }
    }

    private class SharedLibraryLinkOutputs extends LibraryOutputs {
        @Override
        public String getDisplayName() {
            return "Link files for " + DefaultSharedLibraryBinarySpec.this.getDisplayName();
        }

        @Override
        protected boolean hasOutputs() {
            return hasSources() && !isResourceOnly();
        }

        @Override
        protected Set<File> getOutputs() {
            return Collections.singleton(getSharedLibraryLinkFile());
        }

        private boolean isResourceOnly() {
            return hasResources() && !hasExportedSymbols();
        }

        private boolean hasResources() {
            for (NativeResourceSet windowsResourceSet : getInputs().withType(NativeResourceSet.class)) {
                if (!windowsResourceSet.getSource().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasExportedSymbols() {
            for (LanguageSourceSet languageSourceSet : getInputs()) {
                if (!(languageSourceSet instanceof NativeResourceSet)) {
                    if (!languageSourceSet.getSource().isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private class SharedLibraryRuntimeOutputs extends LibraryOutputs {
        @Override
        public String getDisplayName() {
            return "Runtime files for " + DefaultSharedLibraryBinarySpec.this.getDisplayName();
        }

        @Override
        protected boolean hasOutputs() {
            return hasSources();
        }

        @Override
        protected Set<File> getOutputs() {
            return Collections.singleton(getSharedLibraryFile());
        }
    }
}
