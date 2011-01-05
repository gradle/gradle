/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.compile

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.file.FileCollection

class SimpleStaleClassCleanerTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    private final TaskOutputsInternal outputs = Mock()
    private final SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(outputs)
    
    def deletesAllPreviousOutputFiles() {
        def file1 = tmpDir.file('file1').createFile()
        def file2 = tmpDir.file('file2').createFile()
        cleaner.destinationDir = tmpDir.dir

        when:
        cleaner.execute()

        then:
        !file1.exists()
        !file2.exists()
        1 * outputs.previousFiles >> { [iterator: { [file1, file2].iterator() }] as FileCollection }
    }

    def doesNotDeleteFilesWhichAreNotUnderTheDestinationDir() {
        def destDir = tmpDir.file('dir')
        def file1 = destDir.file('file1').createFile()
        def file2 = tmpDir.file('file2').createFile()
        cleaner.destinationDir = destDir

        when:
        cleaner.execute()

        then:
        !file1.exists()
        file2.exists()
        1 * outputs.previousFiles >> { [iterator: { [file1, file2].iterator() }] as FileCollection }
    }
}
