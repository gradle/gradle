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
package org.gradle.nativebinaries.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.Library;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractProjectLibraryBinary extends AbstractProjectNativeBinary implements LibraryBinaryInternal {

    protected AbstractProjectLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform targetPlatform, BuildType buildType,
                                           BinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, targetPlatform, buildType, namingScheme, resolver);
    }

    protected boolean hasSources() {
        for (LanguageSourceSet sourceSet : getSource()) {
            if (!sourceSet.getSource().isEmpty()) {
                return true;
            }
            if (sourceSet.hasBuildDependencies()) {
                return true;
            }
        }
        return false;
    }

    public FileCollection getHeaderDirs() {
        return new AbstractFileCollection() {
            public String getDisplayName() {
                return String.format("Headers for %s", getName());
            }

            public Set<File> getFiles() {
                Set<File> headerDirs = new LinkedHashSet<File>();
                for (HeaderExportingSourceSet sourceSet : getSource().withType(HeaderExportingSourceSet.class)) {
                    headerDirs.addAll(sourceSet.getExportedHeaders().getSrcDirs());
                }
                return headerDirs;
            }

            @Override
            public TaskDependency getBuildDependencies() {
                DefaultTaskDependency dependency = new DefaultTaskDependency();
                for (HeaderExportingSourceSet sourceSet : getSource().withType(HeaderExportingSourceSet.class)) {
                    dependency.add(sourceSet.getBuildDependencies());
                }
                return dependency;
            }
        };
    }

    protected abstract class LibraryOutputs extends AbstractFileCollection {
        public final Set<File> getFiles() {
            if (hasOutputs()) {
                return getOutputs();
            }
            return Collections.emptySet();
        }

        public final String getDisplayName() {
            return AbstractProjectLibraryBinary.this.toString();
        }

        public final TaskDependency getBuildDependencies() {
            if (hasOutputs()) {
                return AbstractProjectLibraryBinary.this.getBuildDependencies();
            }
            return new DefaultTaskDependency();
        }

        protected abstract boolean hasOutputs();

        protected abstract Set<File> getOutputs();
    }
}
