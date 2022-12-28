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

package org.gradle.language.swift.internal

import org.gradle.api.internal.component.DefaultSoftwareComponentVariant
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.swift.SwiftPlatform
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultSwiftLibraryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    DefaultSwiftLibrary library

    def setup() {
        library = project.objects.newInstance(DefaultSwiftLibrary, "main")
    }

    def "has display name"() {
        expect:
        library.displayName.displayName == "Swift library 'main'"
        library.toString() == "Swift library 'main'"
    }

    def "has implementation configuration"() {
        expect:
        library.implementationDependencies == project.configurations.implementation
    }

    def "has api configuration"() {
        expect:
        library.apiDependencies == project.configurations.api
    }

    def "can create static binary"() {
        def targetPlatform = Stub(SwiftPlatform)
        def toolChain = Stub(NativeToolChainInternal)
        def platformToolProvider = Stub(PlatformToolProvider)

        expect:
        def binary = library.addStaticLibrary(identity, true, targetPlatform, toolChain, platformToolProvider)
        binary.name == "mainTest"
        binary.debuggable
        !binary.optimized
        binary.testable
        binary.targetPlatform == targetPlatform
        binary.toolChain == toolChain
        binary.platformToolProvider == platformToolProvider

        library.binaries.realizeNow()
        library.binaries.get() == [binary] as Set
    }

    def "can create shared binary"() {
        def targetPlatform = Stub(SwiftPlatform)
        def toolChain = Stub(NativeToolChainInternal)
        def platformToolProvider = Stub(PlatformToolProvider)

        expect:
        def binary = library.addSharedLibrary(identity, true, targetPlatform, toolChain, platformToolProvider)
        binary.name == "mainTest"
        binary.debuggable
        !binary.optimized
        binary.testable
        binary.targetPlatform == targetPlatform
        binary.toolChain == toolChain
        binary.platformToolProvider == platformToolProvider

        library.binaries.realizeNow()
        library.binaries.get() == [binary] as Set
    }

    def "throws exception when development binary is not available"() {
        given:
        library.binaries.realizeNow()

        when:
        library.developmentBinary.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Cannot query the value of property 'developmentBinary' because it has no value available."
    }

    private NativeVariantIdentity getIdentity() {
        return new NativeVariantIdentity("test", null, null, null, true, false, targetMachine(OperatingSystemFamily.WINDOWS, MachineArchitecture.X86_64),
            new DefaultSoftwareComponentVariant("test", AttributeTestUtil.attributesFactory().mutable()),
            new DefaultSoftwareComponentVariant("test", AttributeTestUtil.attributesFactory().mutable())
        )
    }

    private TargetMachine targetMachine(String os, String arch) {
        def objectFactory = TestUtil.objectFactory()
        return Stub(TargetMachine) {
            getOperatingSystemFamily() >> objectFactory.named(OperatingSystemFamily.class, os)
            getArchitecture() >> objectFactory.named(MachineArchitecture.class, arch)
        }
    }
}
