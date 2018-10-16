/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.resource.cached;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Namer;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.gradle.internal.resource.local.GroupedAndNamedUniqueFileStore;

import java.io.File;

public class ExternalResourceFileStore extends GroupedAndNamedUniqueFileStore<String> {

    private static final int NUMBER_OF_GROUPING_DIRS = 1;
    public static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = NUMBER_OF_GROUPING_DIRS + NUMBER_OF_CHECKSUM_DIRS;

    private static final Grouper<String> GROUPER = new Grouper<String>() {
        @Override
        public String determineGroup(String s) {
            return String.valueOf(Math.abs(s.hashCode()) % 100);
        }

        @Override
        public int getNumberOfGroupingDirs() {
            return NUMBER_OF_GROUPING_DIRS;
        }
    };

    private static final Namer<String> NAMER = new Namer<String>() {
        @Override
        public String determineName(String s) {
            return StringUtils.substringAfterLast(s, "/");
        }
    };

    public ExternalResourceFileStore(File baseDir, TemporaryFileProvider tmpProvider, FileAccessTimeJournal fileAccessTimeJournal) {
        super(baseDir, tmpProvider, fileAccessTimeJournal, GROUPER, NAMER);
    }
}
