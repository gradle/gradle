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

package org.gradle.nativeplatform.tasks

import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class LinkSharedLibraryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def link = TestUtil.createRootProject(tmpDir.testDirectory).tasks.create("link", LinkSharedLibrary)

    def "has no default import library location when platform does not produce one"() {
        def toolChain = Stub(NativeToolChainInternal)
        def platform = Stub(NativePlatformInternal)
        def provider = Stub(PlatformToolProvider)
        toolChain.select(platform) >> provider
        provider.producesImportLibrary() >> false

        when:
        link.toolChain.set(toolChain)
        link.targetPlatform.set(platform)

        then:
        !link.linkedFile.present
        !link.importLibrary.present

        when:
        link.linkedFile = tmpDir.file("shared/lib.dll")

        then:
        !link.importLibrary.present
    }

    def "default import library location is calculated from binary file location"() {
        def toolChain = Stub(NativeToolChainInternal)
        def platform = Stub(NativePlatformInternal)
        def provider = Stub(PlatformToolProvider)
        toolChain.select(platform) >> provider
        provider.producesImportLibrary() >> true
        provider.getImportLibraryName(_) >> { String n -> n.replace(".dll", ".lib") }

        when:
        link.toolChain.set(toolChain)
        link.targetPlatform.set(platform)

        then:
        !link.linkedFile.present
        !link.importLibrary.present

        when:
        link.linkedFile = tmpDir.file("shared/lib.dll")

        then:
        link.importLibrary.get().asFile == tmpDir.file("shared/lib.lib")

        when:
        link.importLibrary.set(tmpDir.file("import/lib_debug.lib"))
        link.linkedFile.set(tmpDir.file("ignore-me"))

        then:
        link.importLibrary.get().asFile == tmpDir.file("import/lib_debug.lib")
    }
}
