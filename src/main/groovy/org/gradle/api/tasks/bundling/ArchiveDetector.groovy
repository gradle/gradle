/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.api.tasks.util.ZipFileSet
import org.gradle.api.tasks.util.TarFileSet

/**
 * @author Hans Dockter
 */
class ArchiveDetector {
    List zipSuffixes = ['zip', 'jar', 'war', 'ear']
    List tarSuffixes = ["tar", "tar.gz", "tgz", "tar.bz", "tbz2"]

    Class archiveFileSetType(File file) {
        if (isZipArchive(file.name)) { return ZipFileSet }
        if (isTarArchive(file.name)) { return TarFileSet }
        null
    }

    boolean isZipArchive(String filename) {
        isArchive(zipSuffixes, filename)
    }

    boolean isTarArchive(String filename) {
        isArchive(tarSuffixes, filename)
    }

    private boolean isArchive(List suffixes, String filename) {
        suffixes.any {
            filename.endsWith('.' + it)
        }
    }
}
