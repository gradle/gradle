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
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.binaryinfo.BinaryInfo
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class UnexportMainSymbolIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
            apply plugin: "swift-application"
            task unexport(type: UnexportMainSymbol) {
                outputDirectory = layout.buildDirectory.dir("relocated")
                objects.from { components.main.developmentBinary.get().objects }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle-native/issues/304")
    def "clean build works"() {
        writeMainSwift()

        expect:
        succeeds("clean", "unexport")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/297")
    def "unexport is incremental"() {
        writeMainSwift()

        when:
        succeeds("unexport")
        then:
        result.assertTasksNotSkipped(":compileDebugSwift", ":unexport")

        when:
        succeeds("unexport")
        then:
        result.assertTasksSkipped(":compileDebugSwift", ":unexport")

        when:
        updateMainSwift()
        succeeds("unexport")
        then:
        result.assertTasksNotSkipped(":compileDebugSwift", ":unexport")
    }

    def "relocate _main symbol with main.swift"() {
        writeMainSwift()

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/main.o")
    }

    def "relocate _main symbol with notMain.swift"() {
        writeMainSwift("notMain.swift")

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/notMain.o")
    }

    def "relocate _main symbol with multiple swift files"() {
        writeMainSwift()
        file("src/main/swift/other.swift") << """
            class Other {}
        """

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/main.o")
        file("build/relocated").assertHasDescendants("main.o", "other.o")
    }

    def "can relocate when there is no main symbol"() {
        file("src/main/swift/notMain.swift") << """
            class NotMain {}
        """
        file("src/main/swift/other.swift") << """
            class Other {}
        """

        when:
        succeeds("unexport")
        then:
        file("build/relocated").assertHasDescendants("notMain.o", "other.o")
    }

    private TestFile writeMainSwift(String filename="main.swift") {
        file("src/main/swift/${filename}") << """
            print("hello world!")
        """
    }

    private TestFile updateMainSwift(String filename="main.swift") {
        file("src/main/swift/${filename}") << """
            print("goodbye world!")
        """
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
