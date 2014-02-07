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

package org.gradle.nativebinaries.internal.resolve;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.nativebinaries.NativeDependencySet;

import java.io.File;
import java.util.Set;

public class SourceSetNativeDependencyResolver implements NativeDependencyResolver {
    private final NativeDependencyResolver delegate;

    public SourceSetNativeDependencyResolver(NativeDependencyResolver delegate) {
        this.delegate = delegate;
    }

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
            return new LanguageSourceSetNativeDependencySet((HeaderExportingSourceSet) sourceSet);
        }
        return new EmptyNativeDependencySet();
    }

    private static class EmptyNativeDependencySet implements NativeDependencySet {
        public FileCollection getIncludeRoots() {
            return empty();
        }

        public FileCollection getLinkFiles() {
            return empty();
        }

        public FileCollection getRuntimeFiles() {
            return empty();
        }

        private FileCollection empty() {
            return new SimpleFileCollection();
        }
    }

    private static class LanguageSourceSetNativeDependencySet extends EmptyNativeDependencySet {
        private final HeaderExportingSourceSet sourceSet;

        private LanguageSourceSetNativeDependencySet(HeaderExportingSourceSet sourceSet) {
            this.sourceSet = sourceSet;
        }

        public FileCollection getIncludeRoots() {
            return new AbstractFileCollection() {
                @Override
                public String getDisplayName() {
                    return "Include roots of " + sourceSet.getName();
                }

                public Set<File> getFiles() {
                    return sourceSet.getExportedHeaders().getSrcDirs();
                }

                @Override
                public TaskDependency getBuildDependencies() {
                    return sourceSet.getBuildDependencies();
                }
            };
        }
    }
}
