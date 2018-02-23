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

import org.gradle.api.Buildable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencies;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.platform.base.LibraryBinarySpec;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractNativeLibraryBinarySpec extends AbstractNativeBinarySpec implements LibraryBinarySpec {

    @Override
    public NativeLibrarySpec getComponent() {
        return getComponentAs(NativeLibrarySpec.class);
    }

    @Override
    public NativeLibrarySpec getLibrary() {
        return getComponentAs(NativeLibrarySpec.class);
    }

    protected boolean hasSources() {
        for (LanguageSourceSet sourceSet : getInputs()) {
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
        return getFileCollectionFactory().create(new HeaderFileSet());
    }

    protected abstract class LibraryOutputs implements MinimalFileSet, Buildable {
        @Override
        public final Set<File> getFiles() {
            if (hasOutputs()) {
                return getOutputs();
            }
            return Collections.emptySet();
        }

        @Override
        public final TaskDependency getBuildDependencies() {
            if (hasOutputs()) {
                return AbstractNativeLibraryBinarySpec.this.getBuildDependencies();
            }
            return TaskDependencies.EMPTY;
        }

        protected abstract boolean hasOutputs();

        protected abstract Set<File> getOutputs();
    }

    private class HeaderFileSet implements MinimalFileSet, Buildable {
        @Override
        public String getDisplayName() {
            return "Headers for " + AbstractNativeLibraryBinarySpec.this.getDisplayName();
        }

        @Override
        public Set<File> getFiles() {
            Set<File> headerDirs = new LinkedHashSet<File>();
            for (HeaderExportingSourceSet sourceSet : getInputs().withType(HeaderExportingSourceSet.class)) {
                headerDirs.addAll(sourceSet.getExportedHeaders().getSrcDirs());
            }
            return headerDirs;
        }

        @Override
        public TaskDependency getBuildDependencies() {
            DefaultTaskDependency dependency = new DefaultTaskDependency();
            for (HeaderExportingSourceSet sourceSet : getInputs().withType(HeaderExportingSourceSet.class)) {
                dependency.add(sourceSet.getBuildDependencies());
            }
            return dependency;
        }
    }
}
