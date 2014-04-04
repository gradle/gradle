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

package org.gradle.integtests.fixtures

import groovy.io.FileType
import org.apache.commons.io.FilenameUtils

import static org.spockframework.util.CollectionUtil.asSet

class OutputsTrackingFixture {

    private final File targetDir

    //Tracks outputs in given target dir
    OutputsTrackingFixture(File targetDir) {
        assert targetDir != null
        this.targetDir = targetDir
    }

    // Executes optional operation and makes a snapshot of outputs (sets the last modified timestamp to zero for all files)
    public <T> T snapshot(Closure<T> operation = null) {
        T result = operation?.call()
        targetDir.eachFileRecurse(FileType.FILES) { it.lastModified = 0 }
        result
    }

    //asserts none of the files changed since last snapshot
    void noneChanged() {
        changedFiles([])
    }

    //asserts file changed since last snapshot
    void changedFile(File file) {
        changedFiles([file])
    }

    //asserts files changed since last snapshot
    void changedFiles(Collection<File> files) {
        def expectedNames = files.collect({ FilenameUtils.removeExtension(it.name) }) as Set
        assert changedFileNames == expectedNames
    }

    //asserts classes changed since last snapshot. Class means file name without extension.
    void changedClasses(String ... classNames) {
        assert changedFileNames == asSet(classNames)
    }

    private Set<String> getChangedFileNames() {
        // Get all of the files that do not have a zero last modified timestamp
        def changed = new HashSet()
        targetDir.eachFileRecurse(FileType.FILES) {
            if (it.lastModified() > 0) {
                changed << FilenameUtils.removeExtension(it.name)
            }
        }
        changed
    }
}