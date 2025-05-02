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

import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.test.fixtures.file.TestFile

import static org.junit.Assume.assumeFalse

class CppIncrementalBuildIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {

    private static final String LIBRARY = ':library'
    private static final String APP = ':app'

    TestFile appSourceFile
    TestFile appOtherSourceFile
    TestFile appHeaderFile
    TestFile libraryHeaderFile
    TestFile libraryImplHeaderFile
    TestFile librarySourceFile
    TestFile libraryOtherSourceFile
    String sourceType = "cpp"
    def libObjects = new CompilationOutputsFixture(file("library/build/obj/main/debug"), [".o", ".obj"])
    def appObjects = new CompilationOutputsFixture(file("app/build/obj/main/debug"), [".o", ".obj"])

    def install = installation("app/build/install/main/debug")
    def libraryDebug = tasks(LIBRARY).debug
    def appDebug = tasks(APP).debug
    def installApp = appDebug.install

    def setup() {
        assumeFalse(toolChain.family == AvailableToolChains.ToolFamily.CYGWIN_GCC) // [test setup issue] fails with - greet.cpp:2:22: fatal error: app.hpp: No such file or directory
        buildFile << """
            project(':library') {
                apply plugin: 'cpp-library'
            }
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':library')
                }
            }
        """
        createDirs("library", "app")
        settingsFile << """
            rootProject.name = 'test'
            include 'library', 'app'
        """

        appHeaderFile = file("app/src/main/cpp/app.hpp") << """
            #include <string>
            extern void greeting(const char* name, std::string& result);
        """

        appSourceFile = file("app/src/main/cpp/main.cpp") << """
            #include <lib.h>
            #include <iostream>
            #include "app.hpp"

            using namespace std;

            int main() {
                string msg;
                greeting("world", msg);
                log(msg);
                return 0;
            }
        """

        appOtherSourceFile = file("app/src/main/cpp/greet.cpp")
        appOtherSourceFile << """
            #include "app.hpp"
            #define PREFIX "hello"

            using namespace std;

            extern void greeting(const char* name, string& result) {
                result.append(PREFIX);
                result.append(" ");
                result.append(name);
            }
        """

        libraryHeaderFile = file("library/src/main/public/lib.h")
        libraryHeaderFile << """
            #pragma once
            #include <string>

            #ifdef _WIN32
            #define EXPORT_FUNC __declspec(dllexport)
            #else
            #define EXPORT_FUNC
            #endif

            void EXPORT_FUNC log(const std::string& message);
            void EXPORT_FUNC log(const char* message);
        """

        libraryImplHeaderFile = file("library/src/main/headers/lib_impl.h")
        libraryImplHeaderFile << """
            #pragma once
        """

        librarySourceFile = file("library/src/main/cpp/log_string.cpp")
        librarySourceFile << """
            #include <lib.h>
            #include <lib_impl.h>
            #include <iostream>

            using namespace std;

            void log(const string& message) {
                cout << message;
            }
        """
        libraryOtherSourceFile = file("library/src/main/cpp/log_chars.cpp")
        libraryOtherSourceFile << """
            #include <lib.h>
            #include <iostream>
            #include "lib_impl.h"

            using namespace std;

            void log(const char* message) {
                cout << message;
            }
        """
    }

    def "rebuilds executable with single source file change"() {
        given:
        run installApp
        libObjects.snapshot()
        appObjects.snapshot()

        when:
        appSourceFile.replace("world", "planet")

        and:
        run installApp

        then:
        result.assertTasksExecuted(libraryDebug.allToLink, appDebug.allToInstall)
        result.assertTasksSkipped(libraryDebug.allToLink)
        result.assertTasksNotSkipped(appDebug.allToInstall)

        and:
        libObjects.noneRecompiled()
        appObjects.recompiledFile(appSourceFile)
        // Test assumes that the app has multiple source files and only one of them has changed. Verify that assumption
        appObjects.hasFiles(appSourceFile, appOtherSourceFile)

        and:
        install.assertInstalled()
        install.exec().out == "hello planet"

        when:
        run installApp

        then:
        allSkipped()
    }

    def "recompiles library and relinks executable after single library source file change"() {
        given:
        run installApp
        libObjects.snapshot()
        appObjects.snapshot()

        when:
        librarySourceFile.replace("cout << message", 'cout << "[" << message << "]"')

        and:
        run installApp

        then:
        result.assertTasksExecuted(libraryDebug.allToLink, appDebug.allToInstall)
        if (toolChain.visualCpp) {
            // App link may or may not be required
            skipped appDebug.compile
            executed libraryDebug.compile, libraryDebug.link
            executed appDebug.install
        } else {
            result.assertTasksSkipped(appDebug.compile)
            result.assertTasksNotSkipped(libraryDebug.allToLink, appDebug.link, appDebug.install)
        }

        and:
        appObjects.noneRecompiled()
        libObjects.recompiledFile(librarySourceFile)

        and:
        install.assertInstalled()
        install.exec().out == "[hello world]"

        when:
        run installApp

        then:
        allSkipped()
    }

    def "recompiles binary and does not relink when public header file changes in a way that does not affect the object files"() {
        given:
        run installApp
        libObjects.snapshot()
        appObjects.snapshot()

        when:
        libraryHeaderFile << """
            // Comment added to the end of the header file
        """
        run installApp

        then:
        executedAndNotSkipped libraryDebug.compile
        executedAndNotSkipped appDebug.compile

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed libraryDebug.link
            executed appDebug.link, installApp
        } else {
            skipped libraryDebug.link
            skipped appDebug.link, installApp
        }

        and:
        appObjects.recompiledFile(appSourceFile)
        libObjects.recompiledFiles(librarySourceFile, libraryOtherSourceFile)

        when:
        run installApp

        then:
        allSkipped()
    }

    def "recompiles binary when implementation header file changes"() {
        given:
        run installApp
        libObjects.snapshot()
        appObjects.snapshot()

        when:
        libraryImplHeaderFile << """
            #define UNUSED_MACRO 123
        """
        run installApp

        then:
        executedAndNotSkipped libraryDebug.compile
        skipped appDebug.compile

        if (nonDeterministicCompilation) {
            // Relinking may (or may not) be required after recompiling
            executed libraryDebug.link
            executed appDebug.link, installApp
        } else {
            skipped libraryDebug.link
            skipped appDebug.link, installApp
        }

        and:
        appObjects.noneRecompiled()
        libObjects.recompiledFiles(librarySourceFile, libraryOtherSourceFile)

        when:
        run installApp

        then:
        allSkipped()
    }

    def "recompiles only those source files affected by a header file change"() {
        given:
        def greetingHeader = file("app/src/main/headers/greeting.hpp")
        greetingHeader << """
            #define PREFIX "hello"
        """
        appOtherSourceFile.text = """
            #include "app.hpp"
            #include "greeting.hpp"

            using namespace std;

            void greeting(const char* name, string& result) {
                result.append(PREFIX);
                result.append(" ");
                result.append(name);
            }
        """

        run installApp
        libObjects.snapshot()
        appObjects.snapshot()

        when:
        greetingHeader.replace("hello", "hi")
        run installApp

        then:
        result.assertTasksSkipped(libraryDebug.allToLink)
        executedAndNotSkipped appDebug.compile

        and:
        libObjects.noneRecompiled()
        appObjects.recompiledFile(appOtherSourceFile)
        // Test assumes there are multiple source files: one that includes the header and one that does not. Verify that assumption
        appObjects.hasFiles(appSourceFile, appOtherSourceFile)

        when:
        libObjects.snapshot()
        appObjects.snapshot()

        run installApp

        then:
        allSkipped()

        and:
        libObjects.noneRecompiled()
        appObjects.noneRecompiled()
    }

    def "considers only those headers that are reachable from source files as inputs"() {
        given:
        def unused = file("app/src/main/headers/ignore1.h") << "broken!"
        def unusedPrivate = file("app/src/main/cpp/ignore2.h") << "broken!"

        run installApp
        libObjects.snapshot()
        appObjects.snapshot()

        when:
        unused << "even more broken"
        unusedPrivate << "even more broken"
        file("src/main/headers/ignored3.h") << "broken"
        file("src/main/headers/some-dir").mkdirs()
        file("src/main/cpp/ignored4.h") << "broken"
        file("src/main/cpp/some-dir").mkdirs()

        run installApp

        then:
        allSkipped()

        when:
        unused.delete()
        unusedPrivate.delete()

        run installApp

        then:
        allSkipped()

        when:
        libraryHeaderFile << """
            int unused();
        """
        run installApp

        then:
        executedAndNotSkipped libraryDebug.compile
        executedAndNotSkipped appDebug.compile

        and:
        appObjects.recompiledFile(appSourceFile)
        libObjects.recompiledFiles(librarySourceFile, libraryOtherSourceFile)
    }

    def "header file referenced using relative path is considered an input"() {
        given:
        def unused = file("app/src/main/headers/ignore1.h") << "broken!"
        appOtherSourceFile.replace('#define PREFIX "hello"', '#include "../not_included/hello.h"')

        def headerFile = file("app/src/main/not_included/hello.h") << """
            const char* get_hello();
            #define PREFIX get_hello()
        """

        def sourceFile = file("app/src/main/cpp/hello.cpp")
        sourceFile.text = """
            #include <iostream>
            #include "../not_included/hello.h"

            const char* get_hello() {
                return "Hi";
            }
        """

        run installApp
        appObjects.snapshot()
        libObjects.snapshot()

        when:
        succeeds installApp
        install.exec().out == "Hi world"

        then:
        allSkipped()

        when:
        headerFile << "void another_thing();"

        then:
        succeeds installApp

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appOtherSourceFile, sourceFile)
        libObjects.noneRecompiled()

        and:
        install.exec().out == "Hi world"

        when:
        unused << "broken again"

        then:
        succeeds installApp
        install.exec().out == "Hi world"

        and:
        allSkipped()
    }

    def "header file referenced using macro #macro is considered an input"() {
        when:
        def unused = file("app/src/main/headers/ignore1.h") << "broken!"

        file("app/src/main/headers/defs.h") << """
            #define _HELLO_HEADER_2 "hello.h"
            #define _HELLO_HEADER_1 _HELLO_HEADER_2
            #define HELLO_HEADER MACRO_FUNCTION() // some indirection

            #define MACRO_FUNCTION( ) _HELLO_HEADER_1
            #define FUNCTION_RETURNS_STRING(X) "hello.h"
            #define FUNCTION_RETURNS_MACRO(X) HELLO_HEADER
            #define FUNCTION_RETURNS_MACRO_CALL(X) FUNCTION_RETURNS_ARG(X)
            #define FUNCTION_RETURNS_ARG(X) X

            #define PREFIX MACRO_USES
            #define SUFFIX() _FUNCTION
            #define ARGS (MACRO_FUNCTION())

            // Token concatenation ## does not macro expand macro function args, so is usually wrapped by another macro function
            #define CONCAT_FUNCTION2(X, Y) X ## Y
            #define CONCAT_FUNCTION(X, Y) CONCAT_FUNCTION2(X, Y)
        """

        def headerFile = file("app/src/main/headers/hello.h") << """
            #define MESSAGE "one"
        """

        appSourceFile.text = """
            #include "defs.h"
            #define MACRO_USES_ANOTHER_MACRO HELLO_HEADER
            #define MACRO_USES_STRING_CONSTANT "hello.h"
            #define MACRO_USES_SYSTEM_PATH <hello.h>
            #define MACRO_USES_FUNCTION MACRO_FUNCTION()
            #define MACRO_USES_FUNCTION_WITH_ARGS FUNCTION_RETURNS_MACRO_CALL(MACRO_USES_FUNCTION)
            #define MACRO_USES_CONCAT_FUNCTION CONCAT_FUNCTION(PREFIX, SUFFIX())
            #ifdef _MSC_VER // only for Visual C++
                #define MACRO_PRODUCES_FUNCTION_CALL CONCAT_FUNCTION(FUNCTION_RETURNS_ARG, ARGS)
            #else
                #define MACRO_PRODUCES_FUNCTION_CALL "hello.h" // ignore
            #endif
            #include ${macro}
            #include <iostream>

            int main () {
              std::cout << MESSAGE;
              return 0;
            }
        """

        then:
        succeeds installApp

        and:
        assert install.exec().out == "one"

        when:
        succeeds installApp
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        allSkipped()

        when:
        headerFile.replace('one', 'two')
        succeeds installApp

        then:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        and:
        install.exec().out == "two"

        when:
        unused << "more broken"
        succeeds installApp

        then:
        allSkipped()

        where:
        macro << [
            "MACRO_USES_STRING_CONSTANT",
            "MACRO_USES_SYSTEM_PATH",
            "MACRO_USES_ANOTHER_MACRO",
            "MACRO_USES_FUNCTION",
            "MACRO_USES_FUNCTION_WITH_ARGS",
            "MACRO_USES_CONCAT_FUNCTION",
            "MACRO_PRODUCES_FUNCTION_CALL",
            "MACRO_FUNCTION()",
            "FUNCTION_RETURNS_STRING(ignore)",
            "FUNCTION_RETURNS_MACRO(ignore)",
            "FUNCTION_RETURNS_ARG(HELLO_HEADER)"
        ]
    }

    def "header file referenced using external macro #macro is considered an input"() {
        when:
        def unused = file("app/src/main/headers/ignore1.h") << "broken!"

        file("app/src/main/headers/defs.h") << """
            #define HEADER "hello.h"
            #define HEADER_FUNC() "hello.h"
        """

        def headerFile = file("app/src/main/headers/hello.h") << """
            #define MESSAGE "one"
        """

        appSourceFile.text = """
            #include "defs.h"
            #include MACRO
            #include <iostream>

            int main () {
              std::cout << MESSAGE;
              return 0;
            }
        """

        buildFile << """
            project(':app') {
                tasks.withType(CppCompile) {
                    macros.put('MACRO','${macro}')
                }
            }
        """

        then:
        succeeds installApp

        and:
        assert install.exec().out == "one"

        when:
        succeeds installApp
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        allSkipped()

        when:
        headerFile.replace('one', 'two')
        succeeds installApp

        then:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        and:
        install.exec().out == "two"

        when:
        unused << "more broken"
        succeeds installApp

        then:
        allSkipped()

        where:
        macro << [
            '"hello.h"',
            '<hello.h>',
            'HEADER',
            'HEADER_FUNC()'
        ]
    }

    def "considers all header files as input to source file with complex macro include #include"() {
        when:
        appSourceFile.text = """
            $text
            #include <iostream>

            int main () {
              std::cout << GREETING;
              return 0;
            }
        """

        def headerFile = file("app/src/main/headers/ignore.h") << """
            IGNORE ME
        """

        file("app/src/main/headers/hello.h") << """
            #define GREETING "hello"
        """

        then:
        executer.withArgument("-i")
        succeeds installApp
        output.contains("Cannot locate header file for '#include $include' in source file 'main.cpp'. Assuming changed.")
        install.exec().out == "hello"
        // Test assumes there are 2 source files: one with unresolvable macros and one without. Verify that assumption
        appObjects.hasFiles(appSourceFile, appOtherSourceFile)

        when:
        headerFile.text = "changed"
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        executer.withArgument("-i")
        succeeds installApp

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile
        unresolvedHeadersDetected(appDebug.compile)

        when:
        headerFile.delete()
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        executer.withArgument("-i")
        succeeds installApp

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile
        unresolvedHeadersDetected(appDebug.compile)

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        file("app/src/main/headers/some-dir").mkdirs()

        succeeds installApp

        then:
        allSkipped()

        when:
        file("app/src/main/headers/some-dir").deleteDir()

        succeeds installApp

        then:
        allSkipped()

        when:
        disableTransitiveUnresolvedHeaderDetection()
        headerFile.text = "changed again"

        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp

        and:
        appObjects.noneRecompiled()
        libObjects.noneRecompiled()

        and:
        skipped appDebug.compile
        skipped libraryDebug.compile

        where:
        include             | text
        'HELLO'             | '''
            #define _HELLO(X) #X
            #define HELLO _HELLO(hello.h)
            #include HELLO
        '''
        '_HELLO(hello . h)' | '''
            #define _HELLO(X) #X
            #include _HELLO(hello.h)
        '''
        'MISSING'           | '''
            #ifdef MISSING
            #include MISSING
            #else
            #include "hello.h"
            #endif
        '''
        'GARBAGE'           | '''
            #if 0
            #define GARBAGE a b c
            #include GARBAGE
            #else
            #include "hello.h"
            #endif
        '''
        'a b c'             | '''
            #if 0
            #include a b c
            #else
            #include "hello.h"
            #endif
        '''
    }

    def "does not consider all header files as inputs if complex macro include is found in dependency and special flag is active"() {
        when:

        appSourceFile.text = """
            #include "headers.h"
            #include <iostream>

            int main () {
              std::cout << GREETING;
              return 0;
            }
        """
        file("app/src/main/headers/headers.h") << """
            #define _HELLO(X) #X
            #define HELLO _HELLO(hello.h)
            #include HELLO
        """
        file("app/src/main/headers/hello.h") << """
            #define GREETING "hello"
        """

        def headerFile = file("app/src/main/headers/ignore.h") << """
            IGNORE ME
        """

        then:
        succeeds installApp
        install.exec().out == "hello"

        when:
        appObjects.snapshot()
        libObjects.snapshot()

        and:
        headerFile.text = "changed 1"

        then:
        succeeds installApp, '--info'

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile
        unresolvedHeadersDetected(appDebug.compile)

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        disableTransitiveUnresolvedHeaderDetection()

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        and:
        headerFile.text = "changed 3"

        then:
        succeeds installApp

        and:
        allSkipped()

        and:
        appObjects.noneRecompiled()
        libObjects.noneRecompiled()
    }

    private GradleExecuter disableTransitiveUnresolvedHeaderDetection() {
        executer.beforeExecute {
            withArgument("-Dorg.gradle.internal.native.headers.unresolved.dependencies.ignore=true")
        }
        return executer
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

        appSourceFile.text = """
                #include <iostream>
                #include "hello.h"

                int main () {
                  std::cout << MESSAGE;
                  return 0;
                }
            """

        then:
        succeeds installApp
        install.exec().out == "hello"

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        appObjects.snapshot()
        libObjects.snapshot()

        header1 << """// some extra stuff"""

        then:
        succeeds installApp
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        succeeds installApp

        then:
        allSkipped()
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

        appSourceFile.text = """
            #include <iostream>
            #include "hello.h"

            int main () {
              std::cout << MESSAGE;
              return 0;
            }
        """

        then:
        succeeds installApp
        install.exec().out == "hello"

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        header << """// some extra stuff"""

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        succeeds installApp

        then:
        allSkipped()
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

        appSourceFile.text = """
            #include <iostream>
            #include "hello.h"

            int main () {
              std::cout << MESSAGE;
              return 0;
            }
        """

        then:
        succeeds installApp
        install.exec().out == "two"

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        header2 << """// some extra stuff"""

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        header3 << """// some extra stuff"""

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        header1 << """// some extra stuff"""

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        unused << "more broken"
        succeeds installApp

        then:
        allSkipped()
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

        appSourceFile.text = """
            #include <iostream>
            #include "hello.h"

            int main () {
              std::cout << MESSAGE;
              return 0;
            }
        """

        then:
        succeeds installApp
        install.exec().out == "one"

        when:
        header2 << " // changes"

        then:
        succeeds installApp
        allSkipped()

        when:
        header.replace('"hello1.h"', '"hello2.h"')

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "two"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        header1 << " // changes"
        succeeds installApp

        then:
        allSkipped()
    }

    def "shared header can reference project specific header"() {
        when:
        appSourceFile.replace("log(msg)", "log_info(msg)")
        libraryHeaderFile << """
            #include <local_defs.h>
        """

        def appDefsHeader = file("app/src/main/headers/local_defs.h")
        appDefsHeader << """
            #pragma once
            #include <string>
            #define log_info(msg) log(std::string("[info] ") + msg)
        """

        librarySourceFile.replace("cout << message", "cout << PREFIX << message")

        def libDefsHeader = file("library/src/main/headers/local_defs.h")
        libDefsHeader << """
            #define PREFIX "LOG: "
        """

        then:
        succeeds installApp
        install.exec().out == "LOG: [info] hello world"

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        libDefsHeader.replace('PREFIX "LOG: "', 'PREFIX "* "')
        appObjects.snapshot()
        libObjects.snapshot()

        and:
        succeeds installApp

        then:
        install.exec().out == "* [info] hello world"

        and:
        skipped appDebug.compile
        executedAndNotSkipped libraryDebug.compile

        and:
        appObjects.noneRecompiled()
        libObjects.recompiledFiles(librarySourceFile, libraryOtherSourceFile)

        when:
        appDefsHeader.replace('"[info]', '"INFO:')
        appObjects.snapshot()
        libObjects.snapshot()

        and:
        succeeds installApp

        then:
        install.exec().out == "* INFO: hello world"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()
    }

    def "shared header can reference source file specific header using macro include"() {
        when:
        libraryHeaderFile << """
            #include LOCAL_DEFS
        """

        appSourceFile.insertBefore("#include <lib.h>", "#define LOCAL_DEFS <app_defs.h>")
        appSourceFile.replace("log(msg)", "log_info(msg)")

        def appDefsHeader = file("app/src/main/headers/app_defs.h")
        appDefsHeader << """
            #pragma once
            #include <string>
            #define log_info(msg) log(std::string("[info] ") + msg)
        """

        librarySourceFile.insertBefore("#include <lib.h>", "#define LOCAL_DEFS <lib_str_defs.h>")
        librarySourceFile.replace("cout << message", "cout << PREFIX << message")

        def libDefsHeader1 = file("library/src/main/headers/lib_str_defs.h")
        libDefsHeader1 << """
            #pragma once
            #define PREFIX "LOG: "
        """

        libraryOtherSourceFile.insertBefore("#include <lib.h>", "#define LOCAL_DEFS <lib_char_defs.h>")

        def libDefsHeader2 = file("library/src/main/headers/lib_char_defs.h")
        libDefsHeader2 << """
            #pragma once
            #define PREFIX "LOG: "
        """

        then:
        succeeds installApp
        install.exec().out == "LOG: [info] hello world"

        when:
        succeeds installApp

        then:
        allSkipped()

        when:
        libDefsHeader1.replace('PREFIX "LOG: "', 'PREFIX "* "')
        appObjects.snapshot()
        libObjects.snapshot()

        and:
        succeeds installApp

        then:
        install.exec().out == "* [info] hello world"

        and:
        skipped appDebug.compile
        executedAndNotSkipped libraryDebug.compile

        and:
        appObjects.noneRecompiled()
        libObjects.recompiledFiles(librarySourceFile)

        when:
        appDefsHeader.replace('"[info]', '"INFO:')
        appObjects.snapshot()
        libObjects.snapshot()

        and:
        succeeds installApp

        then:
        install.exec().out == "* INFO: hello world"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()
    }

    def "project specific header can shadow shared header"() {
        when:
        appSourceFile.insertBefore('#include <lib.h>', '#include "common.h"')
        appSourceFile.replace('"world"', 'TARGET')

        def appHeaderInSrcDir = file("app/src/main/cpp/common.h")
        appHeaderInSrcDir << """
            #pragma once
            #define TARGET "everyone"
        """

        def appHeaderInHeaderDir = file("app/src/main/headers/common.h")
        appHeaderInHeaderDir << """
            // empty
        """

        def commonHeader = file("library/src/main/public/common.h")
        commonHeader << """
            // empty
        """

        librarySourceFile.insertBefore('#include <lib.h>', '#include <common.h>')

        then:
        succeeds installApp
        install.exec().out == "hello everyone"

        when:
        appHeaderInHeaderDir.text = "// ignore"

        then:
        succeeds installApp

        and:
        allSkipped()

        when:
        appHeaderInSrcDir.replace('"everyone"', '"world"')

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "hello world"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        commonHeader.text = "// changed"

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "hello world"

        and:
        skipped appDebug.compile
        executedAndNotSkipped libraryDebug.compile

        and:
        appObjects.noneRecompiled()
        libObjects.recompiledFiles(librarySourceFile)

        when:
        commonHeader.text = appHeaderInSrcDir.text
        appHeaderInSrcDir.delete()
        appHeaderInHeaderDir.delete()

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "hello world"

        and:
        skipped appDebug.compile
        executedAndNotSkipped libraryDebug.compile

        and:
        appObjects.noneRecompiled()
        libObjects.recompiledFiles(librarySourceFile)

        when:
        appHeaderInHeaderDir.text = commonHeader.text

        then:
        succeeds installApp
        allSkipped()

        when:
        appHeaderInSrcDir.text = appHeaderInHeaderDir.text
        appHeaderInSrcDir.replace('"world"', '"planet"')

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "hello planet"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()
    }

    def "recompiles when include path changes resolve different headers"() {
        when:
        appSourceFile.insertBefore('#include <lib.h>', '#include <common.h>')
        appSourceFile.replace('"world"', 'TARGET')

        def appHeaderInHeaderDir = file("app/src/main/headers/common.h")
        appHeaderInHeaderDir << """
            #pragma once
            #define TARGET "world"
        """

        def appHeaderInOtherDir = file("app/src/main/include/common.h")
        appHeaderInOtherDir.text = appHeaderInHeaderDir.text

        then:
        succeeds installApp
        install.exec().out == "hello world"

        when:
        buildFile << """
            project(':app') {
                application.privateHeaders.from = ['src/main/include']
            }
        """

        then:
        succeeds installApp
        install.exec().out == "hello world"

        and:
        allSkipped()

        when:
        appHeaderInHeaderDir << """
            // changed
        """

        then:
        succeeds installApp
        install.exec().out == "hello world"

        and:
        allSkipped()

        when:
        appHeaderInOtherDir.replace('"world"', '"universe"')

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "hello universe"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        buildFile << """
            project(':app') {
                application.privateHeaders.from = ['src/main/headers']
            }
        """

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "hello world"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()
    }

    def "recompiles when system headers change"() {
        when:
        appSourceFile.insertBefore('#include <lib.h>', '#include <common.h>')
        appSourceFile.replace('"world"', 'TARGET')

        def systemHeaderInOtherDir = file("app/src/main/system/common.h")
        systemHeaderInOtherDir << """
            #pragma once
            #define TARGET "world"
        """

        buildFile << """
            project(':app') {
                tasks.withType(CppCompile) {
                    systemIncludes.from("src/main/system")
                }
            }
        """

        then:
        succeeds installApp
        install.exec().out == "hello world"

        when:
        succeeds installApp

        then:
        install.exec().out == "hello world"

        and:
        allSkipped()

        when:
        systemHeaderInOtherDir.replace('"world"', '"universe"')

        and:
        appObjects.snapshot()
        libObjects.snapshot()

        then:
        succeeds installApp
        install.exec().out == "hello universe"

        and:
        executedAndNotSkipped appDebug.compile
        skipped libraryDebug.compile

        and:
        appObjects.recompiledFiles(appSourceFile)
        libObjects.noneRecompiled()

        when:
        succeeds installApp

        then:
        install.exec().out == "hello universe"

        and:
        allSkipped()
    }

    private boolean unresolvedHeadersDetected(String taskPath) {
        executed(taskPath)
        output.contains("After parsing the source files, Gradle cannot calculate the exact set of include files for '${taskPath}'. Every file in the include search path will be considered a header dependency.")
    }
}
