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
package org.gradle.internal.nativeplatform.filesystem

import org.gradle.logging.ConfigureLogging
import org.gradle.logging.TestAppender
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class UnsupportedFilePermissionsTest extends Specification {
    def appender = new TestAppender()
    @Rule ConfigureLogging logging = new ConfigureLogging(appender)
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def permissions = new UnsupportedFilePermissions()

    def "warns on first attempt to stat a file"() {
        when:
        permissions.getUnixMode(tmpDir.createFile("file"))
        permissions.getUnixMode(tmpDir.createDir("dir"))

        then:
        appender.toString() == '[WARN Support for reading or changing file permissions is only available on this platform using Java 7 or later.]'
    }

    def "warns on first attempt to chmod a file"() {
        when:
        permissions.chmod(tmpDir.createFile("file"), 0644)
        permissions.chmod(tmpDir.createDir("dir"), 0644)

        then:
        appender.toString() == '[WARN Support for reading or changing file permissions is only available on this platform using Java 7 or later.]'
    }

    def "warns at most once"() {
        when:
        permissions.chmod(tmpDir.createFile("file"), 0644)
        permissions.getUnixMode(tmpDir.createDir("dir"))
        permissions.getUnixMode(tmpDir.createFile("file"))

        then:
        appender.toString() == '[WARN Support for reading or changing file permissions is only available on this platform using Java 7 or later.]'
    }
}
