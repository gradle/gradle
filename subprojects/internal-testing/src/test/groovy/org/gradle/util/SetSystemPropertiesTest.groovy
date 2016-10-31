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

package org.gradle.util


import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files


class SetSystemPropertiesTest extends Specification {
    @Rule
    TemporaryFolder tempRoot

    def originalTempDir

    def setup() {
        originalTempDir = System.getProperty('java.io.tmpdir')
    }

    def cleanup() {
        System.setProperty('java.io.tmpdir', originalTempDir)
        SetSystemProperties.resetTempDirLocation()
    }


    def "cached java.io.tmpdir location used by File.createTempFile should get resetted"() {
        given:
        def tempDir1 = tempRoot.newFolder('temp1')
        def tempDir2 = tempRoot.newFolder('temp2')
        when:
        System.setProperty('java.io.tmpdir', tempDir1.absolutePath)
        SetSystemProperties.resetTempDirLocation()
        File.createTempFile("file", null).text = 'content'
        then:
        tempDir1.listFiles().size() == 1
        when:
        System.setProperty('java.io.tmpdir', tempDir2.absolutePath)
        SetSystemProperties.resetTempDirLocation()
        File.createTempFile("file", null).text = 'content'
        then:
        tempDir2.listFiles().size() == 1
    }

    def "cached java.io.tmpdir location used by Files.createTempFile should get resetted"() {
        given:
        def tempDir1 = tempRoot.newFolder('temp1')
        def tempDir2 = tempRoot.newFolder('temp2')
        when:
        System.setProperty('java.io.tmpdir', tempDir1.absolutePath)
        SetSystemProperties.resetTempDirLocation()
        Files.createTempFile("file", null).toFile().text = 'content'
        then:
        tempDir1.listFiles().size() == 1
        when:
        System.setProperty('java.io.tmpdir', tempDir2.absolutePath)
        SetSystemProperties.resetTempDirLocation()
        Files.createTempFile("file", null).toFile().text = 'content'
        then:
        tempDir2.listFiles().size() == 1
    }
}
