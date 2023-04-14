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

package org.gradle.language.cpp.internal


import org.gradle.language.cpp.CppPlatform
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
class DefaultCppApplicationTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    def application = project.objects.newInstance(DefaultCppApplication, "main")

    def "has display name"() {
        expect:
        application.displayName.displayName == "C++ application 'main'"
        application.toString() == "C++ application 'main'"
    }

    def "has implementation dependencies"() {
        expect:
        application.implementationDependencies == project.configurations['implementation']
    }

    def "has a main publication"() {
        expect:
        application.mainPublication
    }

    def "can add an executable"() {
        expect:
        def exe = application.addExecutable(identity, Stub(CppPlatform), Stub(NativeToolChainInternal), Stub(PlatformToolProvider))
        exe.name == 'mainDebug'
    }

    private NativeVariantIdentity getIdentity() {
        return Stub(NativeVariantIdentity) {
            getName() >> "debug"
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
