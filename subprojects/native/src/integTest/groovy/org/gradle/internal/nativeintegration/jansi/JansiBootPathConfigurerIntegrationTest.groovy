/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.jansi

import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class JansiBootPathConfigurerIntegrationTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    @Rule
    ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    JansiBootPathConfigurer jansiBootPathConfigurer = new JansiBootPathConfigurer()
    JansiStorageLocator jansiLibraryLocator = new JansiStorageLocator()

    def "can unpack native library when accessed by concurrent threads"() {
        when:
        10.times {
            concurrent.start {
                jansiBootPathConfigurer.configure(tmpDir.testDirectory)
            }
        }

        concurrent.finished()

        then:
        def libFile = jansiLibraryLocator.locate(tmpDir.testDirectory).targetLibFile
        libFile.isFile()
        libFile.length() > 0
        def lockFile = new File(libFile.absolutePath + '.lock')
        lockFile.isFile()
        new RandomAccessFile(lockFile, 'rw').readBoolean()
    }
}
