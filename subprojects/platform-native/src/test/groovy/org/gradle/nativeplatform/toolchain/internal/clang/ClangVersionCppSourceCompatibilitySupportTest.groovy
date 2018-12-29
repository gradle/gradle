/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.clang

import org.gradle.nativeplatform.CppSourceCompatibility
import org.gradle.util.VersionNumber
import spock.lang.Specification
import spock.lang.Unroll

class ClangVersionCppSourceCompatibilitySupportTest extends Specification {
    def "default source compatibility is C++98"() {
        expect:
        ClangVersionCppSourceCompatibilitySupport.getDefaultSourceCompatibility() == CppSourceCompatibility.Cpp98
    }

    @Unroll
    def "throw IAE when clang #version requests source compatibility #sourceCompatibility"() {
        VersionNumber versionNumber = VersionNumber.parse(version)

        when:
        ClangVersionCppSourceCompatibilitySupport.getSourceCompatibilityOption(versionNumber, sourceCompatibility)

        then:
        thrown(IllegalArgumentException)

        where:
        version | sourceCompatibility
        '1.0'   | CppSourceCompatibility.Cpp03
        '2.0'   | CppSourceCompatibility.Cpp03
        '2.9'   | CppSourceCompatibility.Cpp03
        '3.0'   | CppSourceCompatibility.Cpp14
        '3.1'   | CppSourceCompatibility.Cpp14
        '3.2'   | CppSourceCompatibility.Cpp17
        '3.3'   | CppSourceCompatibility.Cpp17
        '3.4'   | CppSourceCompatibility.Cpp17
        '3.5.2' | CppSourceCompatibility.Cpp2a
        '4.0'   | CppSourceCompatibility.Cpp2a
    }

    @Unroll
    def "clang #version supports source compatibility #sourceCompatibility with '#arg'"() {
        VersionNumber versionNumber = VersionNumber.parse(version)

        when:
        def c = ClangVersionCppSourceCompatibilitySupport.getSourceCompatibilityOption(versionNumber, sourceCompatibility)

        then:
        c == arg

        where:
        version | sourceCompatibility                  || arg
        '2.9'   | CppSourceCompatibility.Cpp98         || "-std=c++98"
        '2.9'   | CppSourceCompatibility.Cpp98Extended || "-std=gnu++98"
        '8.0'   | CppSourceCompatibility.Cpp98         || "-std=c++98"
        '8.0'   | CppSourceCompatibility.Cpp98Extended || "-std=gnu++98"

        '3.0'   | CppSourceCompatibility.Cpp03         || "-std=c++03"
        '3.0'   | CppSourceCompatibility.Cpp03Extended || "-std=c++03" // below 5.0, gnu++03 is not recognized
        '5.0'   | CppSourceCompatibility.Cpp03         || "-std=c++03"
        '5.0'   | CppSourceCompatibility.Cpp03Extended || "-std=gnu++03"
        '5.0.2' | CppSourceCompatibility.Cpp03         || "-std=c++03"
        '5.0.2' | CppSourceCompatibility.Cpp03Extended || "-std=gnu++03"
        '6.0'   | CppSourceCompatibility.Cpp03         || "-std=c++03"
        '6.0'   | CppSourceCompatibility.Cpp03Extended || "-std=gnu++03"

        '3.0'   | CppSourceCompatibility.Cpp11         || "-std=c++11" // c++11 is never only experimental after 2.9
        '3.0'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++11"
        '4.0'   | CppSourceCompatibility.Cpp11         || "-std=c++11"
        '4.0'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++11"
        '5.0'   | CppSourceCompatibility.Cpp11         || "-std=c++11"
        '5.0'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++11"

        '3.2'   | CppSourceCompatibility.Cpp14         || "-std=c++1y"
        '3.2'   | CppSourceCompatibility.Cpp14Extended || "-std=gnu++1y"
        '3.3'   | CppSourceCompatibility.Cpp14         || "-std=c++1y"
        '3.3'   | CppSourceCompatibility.Cpp14Extended || "-std=gnu++1y"
        '3.4.2' | CppSourceCompatibility.Cpp14         || "-std=c++1y"
        '3.4.2' | CppSourceCompatibility.Cpp14Extended || "-std=gnu++1y"
        '3.5.2' | CppSourceCompatibility.Cpp14         || "-std=c++14" // c++14 stops being experimental at 3.5.2
        '3.5.2' | CppSourceCompatibility.Cpp14Extended || "-std=gnu++14"
        '6.0'   | CppSourceCompatibility.Cpp14         || "-std=c++14"
        '6.0'   | CppSourceCompatibility.Cpp14Extended || "-std=gnu++14"

        '3.5.2' | CppSourceCompatibility.Cpp17         || "-std=c++1z"
        '3.5.2' | CppSourceCompatibility.Cpp17Extended || "-std=gnu++1z"
        '4.0.1' | CppSourceCompatibility.Cpp17         || "-std=c++1z"
        '4.0.1' | CppSourceCompatibility.Cpp17Extended || "-std=gnu++1z"
        '5.0.2' | CppSourceCompatibility.Cpp17         || "-std=c++17" // c++17 stops being experimental at 5.0
        '5.0.2' | CppSourceCompatibility.Cpp17Extended || "-std=gnu++17"

        '5.0.2' | CppSourceCompatibility.Cpp2a         || "-std=c++2a"
        '5.0.2' | CppSourceCompatibility.Cpp2aExtended || "-std=gnu++2a"
        '6.0'   | CppSourceCompatibility.Cpp2a         || "-std=c++2a"
        '6.0'   | CppSourceCompatibility.Cpp2aExtended || "-std=gnu++2a"
    }
}
