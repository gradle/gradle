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
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.nativeplatform.NativeDependencySet;

import java.io.File;
import java.util.Set;

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
            if (resolution.getInput() instanceof LanguageSourceSet) {
                LanguageSourceSet input = (LanguageSourceSet) resolution.getInput();
                resolution.setNativeDependencySet(createNativeDependencySet(input));
            }
        }
        delegate.resolve(nativeBinaryResolveResult);
    }

    private NativeDependencySet createNativeDependencySet(LanguageSourceSet sourceSet) {
        if (sourceSet instanceof HeaderExportingSourceSet) {
            return new LanguageSourceSetNativeDependencySet((HeaderExportingSourceSet) sourceSet, fileCollectionFactory);
        }
        return EmptyNativeDependencySet.INSTANCE;
    }

    private static class EmptyNativeDependencySet implements NativeDependencySet {

        private static final NativeDependencySet INSTANCE = new EmptyNativeDependencySet();

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

    private static class LanguageSourceSetNativeDependencySet implements NativeDependencySet {
        private final HeaderExportingSourceSet sourceSet;
        private final FileCollectionFactory fileCollectionFactory;

        private LanguageSourceSetNativeDependencySet(HeaderExportingSourceSet sourceSet, FileCollectionFactory fileCollectionFactory) {
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
