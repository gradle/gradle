/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal.configure

import org.gradle.api.Project
import org.gradle.runtime.base.internal.BinaryNamingScheme
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ProjectNativeBinaryInitializerTest extends Specification {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    def component = Mock(ProjectNativeComponent)
    def project = Mock(Project)
    def configAction

    def namingScheme = Mock(BinaryNamingScheme)
    def toolChain = Mock(ToolChainInternal)

    def setup() {
        project.buildDir >> tmpDir.testDirectory
        configAction = new ProjectNativeBinaryInitializer(project)
    }

    def "test executable"() {
        def binary = initBinary(NativeExecutableBinaryInternal)

        when:
        toolChain.getExecutableName("base_name") >> "exe_name"

        and:
        configAction.execute(binary)

        then:
        1 * binary.setExecutableFile(tmpDir.testDirectory.file("binaries", "output_dir", "exe_name"))
    }

    def "test shared library"() {
        def binary = initBinary(SharedLibraryBinaryInternal)

        when:
        toolChain.getSharedLibraryName("base_name") >> "shared_library_name"
        toolChain.getSharedLibraryLinkFileName("base_name") >> "shared_library_link_name"

        and:
        configAction.execute(binary)

        then:
        1 * binary.setSharedLibraryFile(tmpDir.testDirectory.file("binaries", "output_dir", "shared_library_name"))
        1 * binary.setSharedLibraryLinkFile(tmpDir.testDirectory.file("binaries", "output_dir", "shared_library_link_name"))
    }

    def "test static library"() {
        def binary = initBinary(StaticLibraryBinaryInternal)

        when:
        toolChain.getStaticLibraryName("base_name") >> "static_library_name"

        and:
        configAction.execute(binary)

        then:
        1 * binary.setStaticLibraryFile(tmpDir.testDirectory.file("binaries", "output_dir", "static_library_name"))
    }

    private <T extends ProjectNativeBinaryInternal> T initBinary(Class<T> type) {
        def binary = Mock(type)
        binary.component >> component
        binary.toolChain >> toolChain
        binary.namingScheme >> namingScheme

        namingScheme.outputDirectoryBase >> "output_dir"
        component.baseName >> "base_name"
        return binary
    }

    interface NativeExecutableBinaryInternal extends NativeExecutableBinary, ProjectNativeBinaryInternal {}
    interface SharedLibraryBinaryInternal extends SharedLibraryBinary, ProjectNativeBinaryInternal {}
    interface StaticLibraryBinaryInternal extends StaticLibraryBinary, ProjectNativeBinaryInternal {}

}
