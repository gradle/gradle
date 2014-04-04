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

class OutputsTrackingFixture {

    private final File targetDir

    OutputsTrackingFixture(File targetDir) {
        assert targetDir != null
        this.targetDir = targetDir
    }

    public <T> T track(Closure<T> operation) {
        T result = operation.call()
        // Set the last modified timestamp to zero for all files
        targetDir.eachFileRecurse(FileType.FILES) { it.lastModified = 0 }
        result
    }

    void assertNoneChanged() {
        assertChanged([])
    }

    void assertChanged(File file) {
        assertChanged([file])
    }

    def assertChanged(Collection<File> files) {
        def expectedNames = files.collect({ FilenameUtils.removeExtension(it.name) }) as Set
        assert getChangedFileNames() == expectedNames
        return true
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