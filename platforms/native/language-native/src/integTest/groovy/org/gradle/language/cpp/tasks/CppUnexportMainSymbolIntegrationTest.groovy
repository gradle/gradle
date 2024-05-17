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

package org.gradle.language.cpp.tasks

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.SourceFile
import org.gradle.language.nativeplatform.tasks.AbstractUnexportMainSymbolIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppGreeter
import org.gradle.nativeplatform.fixtures.app.CppMultiply
import org.gradle.nativeplatform.fixtures.app.CppSourceElement
import org.gradle.nativeplatform.fixtures.app.CppSum
import org.gradle.nativeplatform.fixtures.app.IncrementalCppElement
import org.gradle.nativeplatform.fixtures.app.IncrementalElement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.SourceFileElement
import spock.lang.Issue

class CppUnexportMainSymbolIntegrationTest extends AbstractUnexportMainSymbolIntegrationTest {
    @RequiresInstalledToolChain(ToolChainRequirement.VISUALCPP)
    @Issue("https://github.com/gradle/gradle-native/issues/277")
    @ToBeFixedForConfigurationCache
    def "can relocate Windows specific _wmain symbol"() {
        makeSingleProject()
        file("src/main/cpp/main.cpp") << """
            #include <iostream>

            int wmain(int argc, wchar_t *argv[], wchar_t *envp[] ) {
                std::cout << "hello world!" << std::endl;
                return 0;
            }
        """

        when:
        succeeds("unexport")
        then:
        assertMainSymbolIsNotExported(objectFile("build/relocated/main"))
    }

    @Override
    protected void makeSingleProject() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
            apply plugin: "cpp-application"
            task unexport(type: UnexportMainSymbol) {
                outputDirectory = layout.buildDirectory.dir("relocated")
                objects.from { components.main.developmentBinary.get().objects }
            }
        """
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    List<String> mainSymbols = ["_main", "main", "_wmain", "wmain"]

    protected SourceFileElement mainFile = getMainFile()
    protected SourceFileElement alternateMainFile = new SourceFileElement() {
        final SourceFile sourceFile = new SourceFile("cpp", "main.cpp", """
            #include <iostream>

            int main(int argc, char* argv[]) {
                std::cout << "goodbye world!" << std::endl;
                return 0;
            }
        """)
    }

    @Override
    protected SourceFileElement getOtherFile() {
        return new SourceFileElement() {
            final SourceFile sourceFile = new SourceFile("cpp", "other.cpp", """
            class Other {};
        """)
        }
    }

    @Override
    protected IncrementalElement getComponentUnderTest() {
        return new IncrementalCppElement() {
            @Override
            protected List<IncrementalElement.Transform> getIncrementalChanges() {
                return [modify(mainFile, alternateMainFile)]
            }
        }
    }

    @Override
    protected SourceElement getComponentWithoutMainUnderTest() {
        return new CppSourceElement() {
            final greeter = new CppGreeter()
            final sum = new CppSum()
            final multiply = new CppMultiply()

            @Override
            SourceElement getHeaders() {
                ofElements(greeter.headers, sum.headers, multiply.headers)
            }

            @Override
            SourceElement getSources() {
                ofElements(greeter.sources, sum.sources, multiply.sources)
            }
        }
    }

    @Override
    protected SourceElement getComponentWithOtherFileUnderTest() {
        return SourceElement.ofElements(mainFile, otherFile)
    }

    @Override
    protected SourceFileElement getMainFile(String filenameWithoutExtension) {
        return new SourceFileElement() {
            final SourceFile sourceFile = new SourceFile("cpp", "${filenameWithoutExtension}.cpp", """
            #include <iostream>

            int main(int argc, char* argv[]) {
                std::cout << "hello world!" << std::endl;
                return 0;
            }
        """)
        }
    }
}
