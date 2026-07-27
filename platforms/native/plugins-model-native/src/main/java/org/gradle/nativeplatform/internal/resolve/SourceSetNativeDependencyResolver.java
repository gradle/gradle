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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.Buildable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Set;

@SuppressWarnings("deprecation")
public class SourceSetNativeDependencyResolver implements NativeDependencyResolver {
    private final NativeDependencyResolver delegate;
    private final FileCollectionFactory fileCollectionFactory;

    public SourceSetNativeDependencyResolver(NativeDependencyResolver delegate, FileCollectionFactory fileCollectionFactory) {
        this.delegate = delegate;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public void resolve(NativeBinaryResolveResult nativeBinaryResolveResult) {
        for (NativeBinaryRequirementResolveResult resolution : nativeBinaryResolveResult.getPendingResolutions()) {
            if (resolution.getInput() instanceof org.gradle.language.base.LanguageSourceSet) {
                org.gradle.language.base.LanguageSourceSet input = (org.gradle.language.base.LanguageSourceSet) resolution.getInput();
                resolution.setNativeDependencySet(createNativeDependencySet(input));
            }
        }
        delegate.resolve(nativeBinaryResolveResult);
    }

    private org.gradle.nativeplatform.NativeDependencySet createNativeDependencySet(org.gradle.language.base.LanguageSourceSet sourceSet) {
        if (sourceSet instanceof org.gradle.language.nativeplatform.HeaderExportingSourceSet) {
            return new LanguageSourceSetNativeDependencySet((org.gradle.language.nativeplatform.HeaderExportingSourceSet) sourceSet, fileCollectionFactory);
        }
        return EmptyNativeDependencySet.INSTANCE;
    }

    private static class EmptyNativeDependencySet implements org.gradle.nativeplatform.NativeDependencySet {

        private static final org.gradle.nativeplatform.NativeDependencySet INSTANCE = new EmptyNativeDependencySet();

        @Override
        public FileCollection getIncludeRoots() {
            return FileCollectionFactory.empty();
        }

        @Override
        public FileCollection getLinkFiles() {
            return FileCollectionFactory.empty();
        }

        @Override
        public FileCollection getRuntimeFiles() {
            return FileCollectionFactory.empty();
        }
    }

    private static class LanguageSourceSetNativeDependencySet implements org.gradle.nativeplatform.NativeDependencySet {
        private final org.gradle.language.nativeplatform.HeaderExportingSourceSet sourceSet;
        private final FileCollectionFactory fileCollectionFactory;

        private LanguageSourceSetNativeDependencySet(org.gradle.language.nativeplatform.HeaderExportingSourceSet sourceSet, FileCollectionFactory fileCollectionFactory) {
            this.sourceSet = sourceSet;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public FileCollection getIncludeRoots() {
            return fileCollectionFactory.create(new HeaderFileCollection());
        }

        @Override
        public FileCollection getLinkFiles() {
            return FileCollectionFactory.empty();
        }

        @Override
        public FileCollection getRuntimeFiles() {
            return FileCollectionFactory.empty();
        }

        private class HeaderFileCollection implements MinimalFileSet, Buildable {
            @Override
            public String getDisplayName() {
                return "Include roots of " + sourceSet.getName();
            }

            @Override
            public Set<File> getFiles() {
                return sourceSet.getExportedHeaders().getSrcDirs();
            }

            @Override
            public TaskDependency getBuildDependencies() {
                return sourceSet.getBuildDependencies();
            }
        }
    }
}
