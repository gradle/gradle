/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.file.copy;


import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFilePermissions;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public interface CopySpecResolver {

    Provider<Boolean> getCaseSensitive();

    /**
     * @deprecated Use {@link #getImmutableFilePermissions()} instead. This method is scheduled for removal in Gradle 9.0.
     */
    @Nullable
    @Deprecated
    Integer getFileMode();
    /**
     * @deprecated Use {@link #getImmutableDirPermissions()} instead. This method is scheduled for removal in Gradle 9.0.
     */
    @Nullable
    @Deprecated
    Integer getDirMode();
    Provider<ConfigurableFilePermissions> getFilePermissions();
    Provider<FilePermissions> getImmutableFilePermissions();
    Provider<ConfigurableFilePermissions> getDirPermissions();
    Provider<FilePermissions> getImmutableDirPermissions();
    boolean getIncludeEmptyDirs();
    String getFilteringCharset();

    RelativePath getDestPath();

    /**
     * Returns the source files of this copy spec.
     */
    FileTree getSource();

    /**
     * Returns the source files of this copy spec and all of its children.
     */
    FileTree getAllSource();

    Collection<? extends Action<? super FileCopyDetails>> getAllCopyActions();

    List<String> getAllIncludes();

    List<String> getAllExcludes();

    List<Spec<FileTreeElement>> getAllIncludeSpecs();

    List<Spec<FileTreeElement>> getAllExcludeSpecs();

    DuplicatesStrategy getDuplicatesStrategy();

    boolean isDefaultDuplicateStrategy();

    void walk(Action<? super CopySpecResolver> action);


}
