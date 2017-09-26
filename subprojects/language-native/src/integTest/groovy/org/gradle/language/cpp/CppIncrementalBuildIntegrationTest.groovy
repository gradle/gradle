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

package org.gradle.language.cpp

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.fixtures.file.TestFile

class CppIncrementalBuildIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {

    private static final String LIBRARY = ':library'
    private static final String APP = ':app'

    IncrementalHelloWorldApp app
    TestFile sourceFile
    TestFile headerFile
    TestFile commonHeaderFile
    List<TestFile> librarySourceFiles = []
    String sourceType = 'Cpp'
    String variant = 'Debug'
    String installApp = ":app:install${variant}"

    def "setup"() {
        app = new CppHelloWorldApp()

        buildFile << """    
            project(':library') {
                apply plugin: 'cpp-library'
                library {
                    publicHeaders.from('src/main/headers')
                }
            }
            project(':app') {
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':library')
                }
            }
        """
        settingsFile << """
            rootProject.name = 'test'
            include 'library', 'app'
        """
        sourceFile = app.mainSource.writeToDir(file("app/src/main"))
        headerFile = app.libraryHeader.writeToDir(file("library/src/main"))
        commonHeaderFile = app.commonHeader.writeToDir(file("library/src/main"))
        app.librarySources.each {
            librarySourceFiles << it.writeToDir(file("library/src/main"))
        }
    }

    def "does not re-execute build with no change"() {
        given:
        run installApp

        when:
        run installApp

        then:
        nonSkippedTasks.empty
    }

    def "rebuilds executable with source file change"() {
        given:
        run installApp

        def install = installation("app/build/install/main/${variant.toLowerCase()}")

        when:
        sourceFile.text = app.alternateMainSource.content

        and:
        run installApp

        then:
        skipped compileTasksDebug(LIBRARY)
        skipped linkTaskDebug(LIBRARY)
        executedAndNotSkipped compileTasksDebug(APP)
        executedAndNotSkipped linkTaskDebug(APP)
        executedAndNotSkipped installApp

        and:
        install.assertInstalled()
        install.exec().out == app.alternateOutput
    }

    def "recompiles library and relinks executable with library source file change"() {
        given:
        run installApp
        maybeWait()
        def install = installation("app/build/install/main/debug")

        when:
        for (int i = 0; i < librarySourceFiles.size(); i++) {
            TestFile sourceFile = librarySourceFiles.get(i)
            sourceFile.text = app.alternateLibrarySources[i].content
        }

        and:
        run installApp

        then:
        executedAndNotSkipped compileTasksDebug(LIBRARY)
        executedAndNotSkipped linkTaskDebug(LIBRARY)
        skipped compileTasksDebug(APP)
        executedAndNotSkipped linkTaskDebug(APP)
        executedAndNotSkipped installApp

        and:
        install.assertInstalled()
        install.exec().out == app.alternateLibraryOutput
    }

    def "recompiles binary when header file changes"() {
        given:
        run installApp
        maybeWait()

        when:
        headerFile << """
            int unused();
        """
        run installApp

        then:
        executedAndNotSkipped compileTasksDebug(LIBRARY)
        executedAndNotSkipped compileTasksDebug(APP)

        if (nonDeterministicCompilation()) {
            // Relinking may (or may not) be required after recompiling
            executed linkTaskDebug(LIBRARY)
            executed linkTaskDebug(APP), installApp
        } else {
            skipped linkTaskDebug(LIBRARY)
            skipped linkTaskDebug(APP), installApp
        }
    }

    def "recompiles binary when header file changes in a way that does not affect the object files"() {
        given:
        run installApp
        maybeWait()

        when:
        headerFile << """
            // Comment added to the end of the header file
        """
        run installApp

        then:
        executedAndNotSkipped compileTasksDebug(LIBRARY)
        executedAndNotSkipped compileTasksDebug(APP)

        if (nonDeterministicCompilation()) {
            // Relinking may (or may not) be required after recompiling
            executed linkTaskDebug(LIBRARY)
            executed linkTaskDebug(APP), installApp
        } else {
            skipped linkTaskDebug(LIBRARY)
            skipped linkTaskDebug(APP), installApp
        }
    }

    def "recompiles binary when header file with relative path changes"() {
        when:

        file("app/src/main/cpp/main.cpp").text = """
                #include "../not_included/hello.h"
    
                int main () {
                  sayHello();
                  return 0;
                }
            """

        def headerFile = file("app/src/main/not_included/hello.h") << """
            void sayHello();
        """

        file("app/src/main/cpp/hello.cpp").text = """
            #include <iostream>

            void sayHello() {
                std::cout << "HELLO" << std::endl;
            }
        """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "HELLO\n"

        when:
        headerFile.text = """
            NOT A VALID HEADER FILE
        """
        then:
        fails installApp
        and:
        executedAndNotSkipped compileTasksDebug(APP)
    }

    private boolean nonDeterministicCompilation() {
        // Visual C++ compiler embeds a timestamp in every object file, and ASLR is non-deterministic
        toolChain.visualCpp || objectiveCWithAslr()
    }

    // compiling Objective-C and Objective-Cpp with clang generates
    // random different object files (related to ASLR settings)
    // We saw this behaviour only on linux so far.
    boolean objectiveCWithAslr() {
        return (sourceType == "Objc" || sourceType == "Objcpp") &&
            OperatingSystem.current().isLinux() &&
            toolChain.displayName == "clang"
    }

}
