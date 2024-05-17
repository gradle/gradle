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

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.language.nativeplatform.tasks.AbstractUnexportMainSymbolIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.IncrementalElement
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftElement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.SourceFileElement
import org.gradle.nativeplatform.fixtures.app.SwiftLib

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftUnexportMainSymbolIntegrationTest extends AbstractUnexportMainSymbolIntegrationTest {
    @Override
    protected void makeSingleProject() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
            apply plugin: "swift-application"
            task unexport(type: UnexportMainSymbol) {
                outputDirectory = layout.buildDirectory.dir("relocated")
                objects.from { components.main.developmentBinary.get().objects }
            }
        """
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSwift"
    }

    List<String> mainSymbols = ["_main", "main"]

    protected SourceFileElement mainFile = getMainFile()
    protected SourceFileElement alternateFile = new SourceFileElement() {
        final SourceFile sourceFile = new SourceFile("swift", "main.swift", 'print("goodbye world!")')
    }

    @Override
    protected SourceFileElement getOtherFile() {
        return new SourceFileElement() {
            final SourceFile sourceFile = new SourceFile("swift", "other.swift", 'class Other {}')
        }
    }

    @Override
    protected IncrementalElement getComponentUnderTest() {
        return new IncrementalSwiftElement() {
            @Override
            protected List<IncrementalElement.Transform> getIncrementalChanges() {
                return [modify(mainFile, alternateFile)]
            }

            final String moduleName = "App"
        }
    }

    @Override
    protected SourceElement getComponentWithoutMainUnderTest() {
        return new SwiftLib()
    }

    @Override
    protected SourceElement getComponentWithOtherFileUnderTest() {
        return SourceElement.ofElements(mainFile, otherFile)
    }

    @Override
    protected SourceFileElement getMainFile(String filenameWithoutExtension) {
        return new SourceFileElement() {
            final SourceFile sourceFile = new SourceFile("swift", "${filenameWithoutExtension}.swift", 'print("hello world!")')
        }
    }
}
