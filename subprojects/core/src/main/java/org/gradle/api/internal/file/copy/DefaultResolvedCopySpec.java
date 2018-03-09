/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;

import javax.annotation.Nullable;

public class DefaultResolvedCopySpec implements ResolvedCopySpec {
    private final RelativePath destPath;
    private final FileTree source;
    private final boolean caseSensitive;
    private final boolean includeEmptyDirs;
    private final DuplicatesStrategy duplicatesStrategy;
    private final Integer fileMode;
    private final Integer dirMode;
    private final String filteringCharset;
    private final Iterable<Action<? super FileCopyDetails>> copyActions;

    public DefaultResolvedCopySpec(
        RelativePath destPath,
        FileTree source,
        boolean caseSensitive,
        boolean includeEmptyDirs,
        DuplicatesStrategy duplicatesStrategy,
        @Nullable Integer fileMode,
        @Nullable Integer dirMode,
        String filteringCharset,
        Iterable<Action<? super FileCopyDetails>> copyActions
    ) {
        this.destPath = destPath;
        this.source = source;
        this.caseSensitive = caseSensitive;
        this.includeEmptyDirs = includeEmptyDirs;
        this.duplicatesStrategy = duplicatesStrategy;
        this.fileMode = fileMode;
        this.dirMode = dirMode;
        this.filteringCharset = filteringCharset;
        this.copyActions = copyActions;
    }

    @Override
    public String getDestinationPath() {
        return destPath.toString();
    }

    @Override
    public RelativePath getDestPath() {
        return destPath;
    }

    @Override
    public FileTree getSource() {
        return source;
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    @Override
    public boolean isIncludeEmptyDirs() {
        return includeEmptyDirs;
    }

    @Override
    public DuplicatesStrategy getDuplicatesStrategy() {
        return duplicatesStrategy;
    }

    @Override
    public Integer getFileMode() {
        return fileMode;
    }

    @Override
    public Integer getDirMode() {
        return dirMode;
    }

    @Override
    public String getFilteringCharset() {
        return filteringCharset;
    }

    @Override
    public Iterable<Action<? super FileCopyDetails>> getCopyActions() {
        return copyActions;
    }
}
