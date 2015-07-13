/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.functional.internal

import org.gradle.internal.SystemProperties
import org.gradle.util.GFileUtils
import spock.lang.Shared
import spock.lang.Specification

class IsolatedDaemonHomeTmpDirectoryProviderTest extends Specification {
    @Shared File expectedTmpDir = new File(new File(SystemProperties.instance.javaIoTmpDir), IsolatedDaemonHomeTmpDirectoryProvider.DIR_NAME)
    TmpDirectoryProvider tmpDirectoryProvider = new IsolatedDaemonHomeTmpDirectoryProvider()

    def "can create temporary directory"() {
        when:
        File tmpDir = tmpDirectoryProvider.createDir()

        then:
        tmpDir.exists()
        tmpDir == expectedTmpDir

        cleanup:
        GFileUtils.forceDelete(tmpDir)
    }

    def "existing temporary directory isn't deleted"() {
        when:
        File tmpDir = tmpDirectoryProvider.createDir()

        then:
        tmpDir.exists()
        tmpDir == expectedTmpDir

        when:
        File tmpFile = new File(tmpDir, 'test.txt')
        GFileUtils.touch(tmpFile)
        File existingTmpDir = tmpDirectoryProvider.createDir()

        then:
        tmpDir == existingTmpDir
        tmpFile.exists()

        cleanup:
        GFileUtils.forceDelete(tmpDir)
    }
}
