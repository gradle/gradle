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

package org.gradle.language.swift.tasks

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.binaryinfo.BinaryInfo
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires([TestPrecondition.SWIFT_SUPPORT])
class UnexportMainSymbolIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
            apply plugin: "swift-executable"
            task unexport(type: UnexportMainSymbol) {
                source components.main.developmentBinary.objects
            }
        """
    }

    def "relocate _main symbol with main.swift"() {
        file("src/main/swift/main.swift") << """
            print("hello world!")
        """

        when:
        succeeds("unexport", "assemble")
        then:
        assertMainSymbolIsNotExported("build/tmp/unexport/main.o")
    }

    def "relocate _main symbol with notMain.swift"() {
        file("src/main/swift/notMain.swift") << """
            print("hello world!")
        """

        when:
        succeeds("unexport", "assemble")
        then:
        assertMainSymbolIsNotExported("build/tmp/unexport/notMain.o")
    }

    def "relocate _main symbol with multiple swift files"() {
        file("src/main/swift/main.swift") << """
            print("hello world!")
        """
        file("src/main/swift/other.swift") << """
            class Other {}
        """

        when:
        succeeds("unexport", "assemble")
        then:
        assertMainSymbolIsNotExported("build/tmp/unexport/main.o")
    }

    def "does not relocate when there is no main.swift"() {
        file("src/main/swift/notMain.swift") << """
            class NotMain {}
        """
        file("src/main/swift/other.swift") << """
            class Other {}
        """

        when:
        succeeds("unexport")
        then:
        file("build/tmp/unexport/main.o").assertDoesNotExist()
    }

    private void assertMainSymbolIsNotExported(String objectFile) {
        assert !findMainSymbol(objectFile).exported
    }

    private BinaryInfo.Symbol findMainSymbol(String objectFile) {
        def binary = new NativeBinaryFixture(file(objectFile), toolChain)
        def symbols = binary.binaryInfo.listSymbols()
        def mainSymbol = symbols.find({ it.name == "_main" || it.name == "main" })
        assert mainSymbol
        mainSymbol
    }
}
