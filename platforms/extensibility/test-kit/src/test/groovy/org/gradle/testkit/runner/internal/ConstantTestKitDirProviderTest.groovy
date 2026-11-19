/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testkit.runner.internal

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.junit.Rule
import spock.lang.Specification

class ConstantTestKitDirProviderTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def "creates the directory and its missing parents"() {
        given:
        def testKitDir = testDirectoryProvider.file('some/dir')

        expect:
        new ConstantTestKitDirProvider(testKitDir).ensureDirExists() == testKitDir
        testKitDir.directory
    }

    def "throws exception if test kit dir is not writable"() {
        given:
        def testKitDir = Mock(File) {
            isDirectory() >> true
            canWrite() >> false
            getAbsolutePath() >> "path"
        }

        when:
        new ConstantTestKitDirProvider(testKitDir).ensureDirExists()

        then:
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Unable to write to test kit directory: path'
    }

    def "throws exception if test kit exists and is not dir"() {
        given:
        def testKitDir = Mock(File) {
            isDirectory() >> false
            exists() >> true
            getAbsolutePath() >> "path"
        }

        when:
        new ConstantTestKitDirProvider(testKitDir).ensureDirExists()

        then:
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Unable to use non-directory as test kit directory: path'
    }

    def "throws exception if test kit dir cannot be created"() {
        given:
        def testKitDir = Mock(File) {
            isDirectory() >> false
            exists() >> false
            mkdirs() >> false
            getAbsolutePath() >> "path"
        }

        when:
        new ConstantTestKitDirProvider(testKitDir).ensureDirExists()

        then:
        Throwable t = thrown(InvalidRunnerConfigurationException)
        t.message == 'Unable to create test kit directory: path'
    }
}
