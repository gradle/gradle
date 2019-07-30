/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.language.nativeplatform.tasks

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.app.IncrementalElement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.SourceFileElement
import org.gradle.nativeplatform.fixtures.binaryinfo.BinaryInfo
import spock.lang.Issue

abstract class AbstractUnexportMainSymbolIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    protected abstract void makeSingleProject()

    protected abstract String getDevelopmentBinaryCompileTask();

    protected abstract IncrementalElement getComponentUnderTest();

    protected abstract SourceElement getComponentWithOtherFileUnderTest();

    protected abstract SourceFileElement getOtherFile();

    protected abstract SourceFileElement getMainFile(String filenameWithoutExtension = "main")

    protected abstract SourceElement getComponentWithoutMainUnderTest()

    protected void assertMainSymbolIsNotExported(String objectFile) {
        assert !findMainSymbol(objectFile).exported
    }

    private BinaryInfo.Symbol findMainSymbol(String objectFile) {
        def binary = new NativeBinaryFixture(file(objectFile), toolChain)
        def symbols = binary.binaryInfo.listSymbols()
        def mainSymbol = symbols.find({ it.name in mainSymbols })
        assert mainSymbol
        mainSymbol
    }

    protected abstract List<String> getMainSymbols()

    @Issue("https://github.com/gradle/gradle-native/issues/304")
    def "clean build works"() {
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        expect:
        succeeds("clean", "unexport")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/297")
    def "unexport is incremental"() {
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("unexport")
        then:
        result.assertTasksNotSkipped(developmentBinaryCompileTask, ":unexport")

        when:
        succeeds("unexport")
        then:
        result.assertTasksSkipped(developmentBinaryCompileTask, ":unexport")

        when:
        componentUnderTest.applyChangesToProject(testDirectory)
        succeeds("unexport")
        then:
        result.assertTasksNotSkipped(developmentBinaryCompileTask, ":unexport")
    }

    def "relocate _main symbol with main.<ext>"() {
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/main.o")
    }

    def "relocate _main symbol with notMain.<ext>"() {
        makeSingleProject()
        getMainFile("notMain").writeToProject(testDirectory)

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/notMain.o")
    }

    def "relocate _main symbol with multiple files"() {
        makeSingleProject()
        componentWithOtherFileUnderTest.writeToProject(testDirectory)

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/main.o")
        file("build/relocated").assertHasDescendants("main.o", "other.o")
    }

    def "relocate _main symbol works incrementally"() {
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/main.o")
        file("build/relocated").assertHasDescendants("main.o")

        when:
        def mainObject = file("build/relocated/main.o")
        mainObject.makeOlder()
        def oldTimestamp = mainObject.lastModified()
        otherFile.writeToProject(testDirectory)
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/main.o")
        file("build/relocated").assertHasDescendants("main.o", "other.o")
        mainObject.lastModified() == oldTimestamp

        when:
        assert file(otherFile.sourceFile.withPath("src/main")).delete()
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported("build/relocated/main.o")
        file("build/relocated").assertHasDescendants("main.o")
        mainObject.lastModified() == oldTimestamp

        when:
        otherFile.writeToProject(testDirectory)
        succeeds("unexport")
        assert file("build/relocated/other.o").delete()
        succeeds("unexport")
        then:
        mainObject.lastModified() > oldTimestamp
    }

    def "can relocate when there is no main symbol"() {
        makeSingleProject()
        componentWithoutMainUnderTest.writeToProject(testDirectory)

        when:
        succeeds("unexport")
        then:
        file("build/relocated").assertHasDescendants("greeter.o", "sum.o", "multiply.o")
    }
}
