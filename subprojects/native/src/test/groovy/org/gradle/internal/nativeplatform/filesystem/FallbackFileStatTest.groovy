/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeplatform.filesystem

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder

class FallbackFileStatTest extends Specification {

    @Rule TemporaryFolder temporaryFolder;

    def "mode() returns FileSystem.DEFAULT_FILE_MODE for files"() {
        setup:
        def testfile = temporaryFolder.createFile("testFile")
        FallbackFileStat fallbackFileStat = new FallbackFileStat(testfile.absolutePath)
        expect:
        FileSystem.DEFAULT_FILE_MODE == fallbackFileStat.mode()
    }

    def "mode() returns FileSystem.DEFAULT_DIR_MODE for directories"() {
        setup:
        def testfolder = temporaryFolder.createDir()
        FallbackFileStat fallbackFileStat = new FallbackFileStat(testfolder.absolutePath)
        expect:
        FileSystem.DEFAULT_DIR_MODE == fallbackFileStat.mode()
    }
}
