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

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class CppIncrementalBuildIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest implements CppTaskNames {

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

    def setup() {
        app = new CppHelloWorldApp()

        buildFile << """    
            project(':library') {
                apply plugin: 'cpp-library'
                library {
                    publicHeaders.from('src/main/headers')
                }
            }
            project(':app') {
                apply plugin: 'cpp-application'
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

        when:
        run installApp

        then:
        nonSkippedTasks.empty
    }

    def "recompiles library and relinks executable after library source file change"() {
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
        executedAndNotSkipped installApp

        and:
        install.assertInstalled()
        install.exec().out == app.alternateLibraryOutput

        when:
        run installApp

        then:
        nonSkippedTasks.empty
    }

    def "considers only those headers that are reachable from source files as inputs"() {
        given:
        def unused = file("app/src/main/headers/ignore1.h") << "broken!"
        def unusedPrivate = file("app/src/main/cpp/ignore2.h") << "broken!"

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

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed linkTaskDebug(LIBRARY)
            executed linkTaskDebug(APP), installApp
        } else {
            skipped linkTaskDebug(LIBRARY)
            skipped linkTaskDebug(APP), installApp
        }

        when:
        run installApp

        then:
        nonSkippedTasks.empty

        when:
        unused << "even more broken"
        unusedPrivate << "even more broken"
        file("src/main/headers/ignored3.h") << "broken"
        file("src/main/headers/some-dir").mkdirs()
        file("src/main/cpp/ignored4.h") << "broken"
        file("src/main/cpp/some-dir").mkdirs()

        run installApp

        then:
        nonSkippedTasks.empty

        when:
        unused.delete()
        unusedPrivate.delete()

        run installApp

        then:
        nonSkippedTasks.empty

        when:
        headerFile.delete()
        fails installApp

        then:
        executedAndNotSkipped compileTasksDebug(LIBRARY)
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

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed linkTaskDebug(LIBRARY)
            executed linkTaskDebug(APP), installApp
        } else {
            skipped linkTaskDebug(LIBRARY)
            skipped linkTaskDebug(APP), installApp
        }

        when:
        run installApp

        then:
        nonSkippedTasks.empty
    }

    def "header file referenced using relative path is considered an input"() {
        given:
        def unused = file("app/src/main/headers/ignore1.h") << "broken!"
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

        run installApp

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty
        executable("app/build/exe/main/debug/app").exec().out == "HELLO\n"

        when:
        headerFile << "// more stuff"

        then:
        succeeds installApp

        and:
        executedAndNotSkipped compileTasksDebug(APP)
        executable("app/build/exe/main/debug/app").exec().out == "HELLO\n"

        when:
        unused << "broken again"
        succeeds installApp

        then:
        nonSkippedTasks.empty
        executable("app/build/exe/main/debug/app").exec().out == "HELLO\n"
    }

    @Unroll
    def "header file referenced using simple macro #macro is considered an input"() {
        when:
        def unused = file("app/src/main/headers/ignore1.h") << "broken!"

        file("app/src/main/headers/defs.h") << """
            #define _HELLO_HEADER_2 "hello.h"
            #define _HELLO_HEADER_1 _HELLO_HEADER_2
            #define HELLO_HEADER MACRO_FUNCTION() // some indirection
            
            #define MACRO_FUNCTION( ) _HELLO_HEADER_1
            #define FUNCTION_RETURNS_STRING(X) "hello.h"
            #define FUNCTION_RETURNS_MACRO(X) HELLO_HEADER
            #define FUNCTION_RETURNS_ARG(X) X
        """

        def headerFile = file("app/src/main/headers/hello.h") << """
            #define MESSAGE "one"
        """

        file("app/src/main/cpp/main.cpp").text = """
            #include "defs.h"
            #define MACRO_USES_ANOTHER_MACRO HELLO_HEADER
            #define MACRO_USES_STRING_CONSTANT "hello.h"
            #define MACRO_USES_SYSTEM_PATH <hello.h>
            #define MACRO_USES_FUNCTION MACRO_FUNCTION()
            #define MACRO_USES_FUNCTION_WITH_ARGS FUNCTION_RETURNS_ARG(FUNCTION_RETURNS_ARG(MACRO_USES_FUNCTION))
            #include ${macro}
            #include <iostream>

            int main () {
              std::cout << MESSAGE << std::endl;
              return 0;
            }
        """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "one\n"

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        headerFile.text = headerFile.text.replace('one', 'two')
        succeeds installApp

        then:
        executedAndNotSkipped compileTasksDebug(APP)
        executable("app/build/exe/main/debug/app").exec().out == "two\n"

        when:
        unused << "more broken"
        succeeds installApp

        then:
        nonSkippedTasks.empty

        where:
        macro << [
            "MACRO_USES_STRING_CONSTANT",
            "MACRO_USES_SYSTEM_PATH",
            "MACRO_USES_ANOTHER_MACRO",
            "MACRO_USES_FUNCTION",
            "MACRO_USES_FUNCTION_WITH_ARGS",
            "MACRO_FUNCTION()",
            "FUNCTION_RETURNS_STRING(ignore)",
            "FUNCTION_RETURNS_MACRO(ignore)",
            "FUNCTION_RETURNS_ARG(HELLO_HEADER)"
        ]
    }

    @Unroll
    def "considers all header files as inputs when complex macro include #include is used"() {
        when:

        file("app/src/main/cpp/main.cpp").text = """
            $text
            #include <iostream>

            int main () {
              std::cout << "hello" << std::endl;
              return 0;
            }
        """

        def headerFile = file("app/src/main/headers/ignore.h") << """
            IGNORE ME
        """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "hello\n"

        when:
        headerFile.text = "changed"

        then:
        executer.withArgument("-i")
        succeeds installApp

        and:
        executedAndNotSkipped compileTasksDebug(APP)
        output.contains("Cannot locate header file for '#include $include' in source file 'main.cpp'. Assuming changed.")
        unresolvedHeadersDetected(':app:dependDebugCpp')

        when:
        disableTransitiveUnresolvedHeaderDetection()
        headerFile.text = "changed again"

        then:
        executer.withArgument("-i")
        succeeds installApp

        and:
        executedAndNotSkipped compileTasksDebug(APP)
        output.contains("Cannot locate header file for '#include $include' in source file 'main.cpp'. Assuming changed.")
        unresolvedHeadersDetected(':app:dependDebugCpp')

        when:
        file("app/src/main/headers/some-dir").mkdirs()

        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        file("app/src/main/headers/some-dir").deleteDir()

        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        headerFile.delete()

        then:
        succeeds installApp

        and:
        executedAndNotSkipped compileTasksDebug(APP)

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty

        where:
        include                | text
        'HELLO'                | '''            
            #define _HELLO(X) #X
            #define HELLO _HELLO(hello.h)
            #include HELLO
        '''
        '_HELLO(hello.h)'      | '''
            #define _HELLO(X) #X
            #include _HELLO(hello.h)
        '''
        'MISSING'              | '''
            #ifdef MISSING
            #include MISSING
            #else
            #include "hello.h"
            #endif
        '''
        'GARBAGE'              | '''
            #if 0
            #define GARBAGE a b c
            #include GARBAGE
            #else
            #include "hello.h"
            #endif
        '''
        'a b c'                | '''
            #if 0
            #include a b c
            #else
            #include "hello.h"
            #endif
        '''
    }

    def "does not consider all header files as inputs if complex macro include is found in dependency and special flag is active"() {
        when:

        file("app/src/main/cpp/main.cpp").text = """
            #include "headers.h"
            #include <iostream>

            int main () {
              std::cout << "hello" << std::endl;
              return 0;
            }
        """
        file("app/src/main/headers/headers.h") << """
            #define _HELLO(X) #X
            #define HELLO _HELLO(hello.h)
            #include HELLO
        """

        def headerFile = file("app/src/main/headers/ignore.h") << """
            IGNORE ME
        """

        disableTransitiveUnresolvedHeaderDetection()

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "hello\n"

        when:
        headerFile.text = "changed"

        then:
        succeeds installApp

        and:
        skipped compileTasksDebug(APP)
    }

    private GradleExecuter disableTransitiveUnresolvedHeaderDetection() {
        executer.with {
            withArgument("-Dorg.gradle.internal.native.headers.unresolved.dependencies.ignore=true")
        }
    }

    def "does consider all header files as inputs if complex macro include is found in dependency"() {
        when:

        file("app/src/main/cpp/main.cpp").text = """
            #include "headers.h"
            #include <iostream>

            int main () {
              std::cout << "hello" << std::endl;
              return 0;
            }
        """
        file("app/src/main/headers/headers.h") << """
            #define _HELLO(X) #X
            #define HELLO _HELLO(hello.h)
            #include HELLO
        """

        def headerFile = file("app/src/main/headers/ignore.h") << """
            IGNORE ME
        """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "hello\n"

        when:
        headerFile.text = "changed"

        then:
        succeeds installApp, '--info'

        and:
        executedAndNotSkipped compileTasksDebug(APP)
        unresolvedHeadersDetected(':app:dependDebugCpp')
    }

    def "can have a cycle between header files"() {
        def header1 = file("app/src/main/headers/hello.h")
        def header2 = file("app/src/main/headers/other.h")

        when:
        header1 << """
            #ifndef HELLO
            #define HELLO
            #include "other.h"
            #endif
        """
        header2 << """
            #ifndef OTHER
            #define OTHER
            #define MESSAGE "hello"
            #include "hello.h"
            #endif
        """

        file("app/src/main/cpp/main.cpp").text = """
                #include <iostream>
                #include "hello.h"
    
                int main () {
                  std::cout << MESSAGE << std::endl;
                  return 0;
                }
            """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "hello\n"

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        header1 << """// some extra stuff"""

        then:
        succeeds installApp
        executedAndNotSkipped compileTasksDebug(APP)

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty
    }

    def "can reference a missing header file"() {
        def header = file("app/src/main/headers/hello.h")

        when:
        header << """
            #pragma once
            #define MESSAGE "hello"
            #if 0
            #include "missing.h"
            #endif
        """

        file("app/src/main/cpp/main.cpp").text = """
            #include <iostream>
            #include "hello.h"

            int main () {
              std::cout << MESSAGE << std::endl;
              return 0;
            }
        """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "hello\n"

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        header << """// some extra stuff"""

        then:
        succeeds installApp
        executedAndNotSkipped compileTasksDebug(APP)

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty
    }

    def "source file can reference multiple header files using the same macro"() {
        def header1 = file("app/src/main/headers/hello1.h")
        def header2 = file("app/src/main/headers/hello2.h")
        def header3 = file("app/src/main/headers/hello3.h")
        def unused = file("app/src/main/headers/ignoreme.h")

        when:
        file("app/src/main/headers/hello.h") << """
            #if 0
            #include "def1.h"
            #else
            #include "def2.h"
            #endif
            #include HEADER
        """
        file("app/src/main/headers/def1.h") << """
            #define HEADER "hello1.h"
        """
        file("app/src/main/headers/def2.h") << """
            #define _HEADER "hello2.h"
            #ifndef _HEADER
            #define _HEADER "hello3.h"
            #endif
            #define HEADER _HEADER
        """
        header1 << """
            #define MESSAGE "one"
        """
        header2 << """
            #define MESSAGE "two"
        """
        header3 << """
            #define MESSAGE "three"
        """
        unused << "broken"

        file("app/src/main/cpp/main.cpp").text = """
            #include <iostream>
            #include "hello.h"

            int main () {
              std::cout << MESSAGE << std::endl;
              return 0;
            }
        """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "two\n"

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        header2 << """// some extra stuff"""

        then:
        succeeds installApp
        executedAndNotSkipped compileTasksDebug(APP)

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        header3 << """// some extra stuff"""

        then:
        succeeds installApp
        executedAndNotSkipped compileTasksDebug(APP)

        when:
        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        header1 << """// some extra stuff"""

        then:
        succeeds installApp
        executedAndNotSkipped compileTasksDebug(APP)

        when:
        unused << "more broken"
        succeeds installApp

        then:
        nonSkippedTasks.empty
    }

    def "changes to the included header graph are reflected in the inputs"() {
        def header = file("app/src/main/headers/hello.h")
        def header1 = file("app/src/main/headers/hello1.h")
        def header2 = file("app/src/main/headers/hello2.h")

        when:
        header << """
            #pragma once
            #include "hello1.h"
        """
        header1 << """
            #define MESSAGE "one"
        """
        header2 << """
            #define MESSAGE "two"
        """

        file("app/src/main/cpp/main.cpp").text = """
            #include <iostream>
            #include "hello.h"

            int main () {
              std::cout << MESSAGE << std::endl;
              return 0;
            }
        """

        then:
        succeeds installApp
        executable("app/build/exe/main/debug/app").exec().out == "one\n"

        when:
        header2 << " // changes"
        succeeds installApp

        then:
        nonSkippedTasks.empty

        when:
        header.text = header.text.replace('"hello1.h"', '"hello2.h"')

        then:
        succeeds installApp
        executedAndNotSkipped compileTasksDebug(APP)
        executable("app/build/exe/main/debug/app").exec().out == "two\n"

        when:
        header1 << " // changes"
        succeeds installApp

        then:
        nonSkippedTasks.empty
    }

    private boolean unresolvedHeadersDetected(String taskPath) {
        output.contains("After parsing the source files, Gradle cannot calculate the exact set of include files for '${taskPath}'. Every file in the include search path will be considered a header dependency.")
    }
}
