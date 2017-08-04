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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

class SwiftHelloWorldApp extends IncrementalHelloWorldApp {
    def greeter = new SwiftGreeter()
    def alternateGreeter = new SwiftAlternateGreeter()
    def sum = new SwiftSum()
    def main = new SwiftMain()
    def alternateMain = new SwiftAlternateMain()

    @Override
    List<SourceFile> getAllFiles() {
        sourceFiles
    }

    @Override
    TestNativeComponent getExecutable() {
        def delegate = super.getExecutable()
        return new TestNativeComponent() {
            @Override
            List<SourceFile> getHeaderFiles() {
                return delegate.getHeaderFiles()
            }

            @Override
            List<SourceFile> getSourceFiles() {
                return delegate.getSourceFiles().collect {
                    sourceFile(it.path, it.name, "import greeter\n${it.content}")
                }
            }
        }
    }

    @Override
    SourceFile getMainSource() {
        return main.sourceFile
    }

    SourceFile getAlternateMainSource() {
        return alternateMain.sourceFile
    }

    String alternateOutput = greeter.expectedOutput

    @Override
    TestNativeComponent getLibrary() {
        def delegate = super.getLibrary()
        return new TestNativeComponent() {
            @Override
            List<SourceFile> getHeaderFiles() {
                return Collections.<SourceFile>emptyList()
            }

            @Override
            List<SourceFile> getSourceFiles() {
                return delegate.getSourceFiles()
            }
        }
    }

    @Override
    SourceFile getLibraryHeader() {
        throw new UnsupportedOperationException()
    }

    @Override
    SourceFile getCommonHeader() {
        throw new UnsupportedOperationException()
    }

    List<SourceFile> librarySources = [
        greeter.sourceFile,
        sum.sourceFile
    ]

    List<SourceFile> alternateLibrarySources = [
        alternateGreeter.sourceFile,
        sum.sourceFile
    ]

    String alternateLibraryOutput = "${alternateGreeter.expectedOutput}${sum.sum(5, 7)}"

    SourceFile getBrokenFile() {
        return sourceFile("swift", "broken.swift", """'broken""")
    }

    @Override
    String getSourceSetType() {
        return "SwiftSourceSet"
    }

    @Override
    List<SourceFile> getSourceFiles() {
        librarySources + [mainSource]
    }
}
