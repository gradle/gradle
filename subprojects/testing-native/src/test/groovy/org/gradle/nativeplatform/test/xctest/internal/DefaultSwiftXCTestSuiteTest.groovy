/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest.internal

import org.gradle.language.swift.SwiftPlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultSwiftXCTestSuiteTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def project = TestUtil.createRootProject(tmpDir.testDirectory)

    def "has implementation dependencies"() {
        def testSuite = new DefaultSwiftXCTestSuite("test", project, project.objects)

        expect:
        testSuite.implementationDependencies == project.configurations.testImplementation
    }

    def "can add a test executable"() {
        def targetPlatform = Stub(SwiftPlatform)
        def toolChain = Stub(NativeToolChainInternal)
        def platformToolProvider = Stub(PlatformToolProvider)
        def testSuite = new DefaultSwiftXCTestSuite("test", project, project.objects)

        expect:
        def exe = testSuite.addExecutable("Executable", targetPlatform, toolChain, platformToolProvider)
        exe.name == 'testExecutable'
        exe.targetPlatform == targetPlatform
        exe.toolChain == toolChain
        exe.platformToolProvider == platformToolProvider
    }

    def "can add a test bundle"() {
        def testSuite = new DefaultSwiftXCTestSuite("test", project, project.objects)

        expect:
        def exe = testSuite.addBundle("Executable", Stub(SwiftPlatform), Stub(NativeToolChainInternal), Stub(PlatformToolProvider))
        exe.name == 'testExecutable'
    }
}
