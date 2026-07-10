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


import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.swift.SwiftPlatform
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultSwiftXCTestSuiteTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    def testSuite = project.objects.newInstance(DefaultSwiftXCTestSuite, "test")

    def "has display name"() {

        expect:
        testSuite.displayName.displayName == "XCTest suite 'test'"
        testSuite.toString() == "XCTest suite 'test'"
    }

    def "has implementation dependencies"() {
        expect:
        testSuite.implementationDependencies == project.configurations.testImplementation
    }

    def "can add a test executable"() {
        def targetPlatform = Stub(SwiftPlatform)
        def toolChain = Stub(NativeToolChainInternal)
        def platformToolProvider = Stub(PlatformToolProvider)

        expect:
        def exe = testSuite.addExecutable(identity, targetPlatform, toolChain, platformToolProvider)
        exe.name == 'testExecutable'
        exe.targetPlatform == targetPlatform
        exe.toolChain == toolChain
        exe.platformToolProvider == platformToolProvider
    }

    def "can add a test bundle"() {
        expect:
        def exe = testSuite.addBundle(identity, Stub(SwiftPlatform), Stub(NativeToolChainInternal), Stub(PlatformToolProvider))
        exe.name == 'testExecutable'
    }

    private NativeVariantIdentity getIdentity() {
        return Stub(NativeVariantIdentity) {
            getTargetMachine() >> targetMachine(OperatingSystemFamily.WINDOWS, MachineArchitecture.X86_64)
        }
    }

    private TargetMachine targetMachine(String os, String arch) {
        def objectFactory = TestUtil.objectFactory()
        return Stub(TargetMachine) {
            getOperatingSystemFamily() >> objectFactory.named(OperatingSystemFamily.class, os)
            getArchitecture() >> objectFactory.named(MachineArchitecture.class, arch)
        }
    }
}
