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

package org.gradle.nativeplatform.test.cpp.internal

import org.gradle.language.cpp.CppPlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultCppTestSuiteTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def project = TestUtil.createRootProject(tmpDir.testDirectory)

    def "can add executable"() {
        def testSuite = new DefaultCppTestSuite("test", project.objects, project, project.configurations)

        expect:
        def exe = testSuite.addExecutable("exe", Stub(CppPlatform), Stub(NativeToolChainInternal), Stub(PlatformToolProvider))
        exe.name == 'testExe'
    }
}
