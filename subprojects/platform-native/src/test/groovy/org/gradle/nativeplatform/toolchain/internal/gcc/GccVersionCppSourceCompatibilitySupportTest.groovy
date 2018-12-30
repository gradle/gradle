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

package org.gradle.nativeplatform.toolchain.internal.gcc

import org.gradle.nativeplatform.CppSourceCompatibility
import org.gradle.util.VersionNumber
import spock.lang.Specification
import spock.lang.Unroll

class GccVersionCppSourceCompatibilitySupportTest extends Specification {
    @Unroll
    def "default gcc #version source compatibility is #sourceCompatibility"() {
        VersionNumber versionNumber = VersionNumber.parse(version)

        expect:
        GccVersionCppSourceCompatibilitySupport.getDefaultSourceCompatibility(versionNumber) == sourceCompatibility

        where:
        version || sourceCompatibility
        '1.0' || CppSourceCompatibility.Cpp98Extended
        '2.0' || CppSourceCompatibility.Cpp98Extended
        '3.0' || CppSourceCompatibility.Cpp98Extended
        '4.0' || CppSourceCompatibility.Cpp98Extended
        '5.0' || CppSourceCompatibility.Cpp98Extended
        '6.0' || CppSourceCompatibility.Cpp98Extended
        '6.1' || CppSourceCompatibility.Cpp14Extended // gcc's default changes
        '6.2' || CppSourceCompatibility.Cpp14Extended
        '7.0' || CppSourceCompatibility.Cpp14Extended
        '8.0' || CppSourceCompatibility.Cpp14Extended
    }

    @Unroll
    def "throw IAE when gcc #version requests source compatibility #sourceCompatibility"() {
        VersionNumber versionNumber = VersionNumber.parse(version)

        when:
        GccVersionCppSourceCompatibilitySupport.getSourceCompatibilityOption(versionNumber, sourceCompatibility)

        then:
        thrown(IllegalArgumentException)

        where:
        version | sourceCompatibility
        '4.2'   | CppSourceCompatibility.Cpp11
        '4.7'   | CppSourceCompatibility.Cpp14
        '4.9'   | CppSourceCompatibility.Cpp17
        '7.3'   | CppSourceCompatibility.Cpp2a
    }

    @Unroll
    def "gcc #version supports source compatibility #sourceCompatibility with '#arg'"() {
        VersionNumber versionNumber = VersionNumber.parse(version)

        when:
        def c = GccVersionCppSourceCompatibilitySupport.getSourceCompatibilityOption(versionNumber, sourceCompatibility)

        then:
        c == arg

        where:
        version | sourceCompatibility                  || arg
        '3.0'   | CppSourceCompatibility.Cpp98         || "-std=c++98"
        '3.0'   | CppSourceCompatibility.Cpp98Extended || "-std=gnu++98"
        '8.0'   | CppSourceCompatibility.Cpp98         || "-std=c++98"
        '8.0'   | CppSourceCompatibility.Cpp98Extended || "-std=gnu++98"

        '4.7'   | CppSourceCompatibility.Cpp03         || "-std=c++98"
        '4.7'   | CppSourceCompatibility.Cpp03Extended || "-std=gnu++98" // below 4.8, c++03 is lumped in with c++98
        '4.8'   | CppSourceCompatibility.Cpp03         || "-std=c++03"
        '4.8'   | CppSourceCompatibility.Cpp03Extended || "-std=gnu++03"
        '5.0'   | CppSourceCompatibility.Cpp03         || "-std=c++03"
        '5.0'   | CppSourceCompatibility.Cpp03Extended || "-std=gnu++03"
        '6.3'   | CppSourceCompatibility.Cpp03         || "-std=c++03"
        '6.3'   | CppSourceCompatibility.Cpp03Extended || "-std=gnu++03"

        '4.3'   | CppSourceCompatibility.Cpp11         || "-std=c++0x"
        '4.3'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++0x"
        '4.6'   | CppSourceCompatibility.Cpp11         || "-std=c++0x"
        '4.6'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++0x"
        '4.7'   | CppSourceCompatibility.Cpp11         || "-std=c++11" // c++11 stops being experimental at 4.7
        '4.7'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++11"
        '4.8'   | CppSourceCompatibility.Cpp11         || "-std=c++11"
        '4.8'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++11"
        '5.0'   | CppSourceCompatibility.Cpp11         || "-std=c++11"
        '5.0'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++11"
        '6.3'   | CppSourceCompatibility.Cpp11         || "-std=c++11"
        '6.3'   | CppSourceCompatibility.Cpp11Extended || "-std=gnu++11"

        '4.8'   | CppSourceCompatibility.Cpp14         || "-std=c++1y"
        '4.8'   | CppSourceCompatibility.Cpp14Extended || "-std=gnu++1y"
        '4.9'   | CppSourceCompatibility.Cpp14         || "-std=c++1y"
        '4.9'   | CppSourceCompatibility.Cpp14Extended || "-std=gnu++1y"
        '5.0'   | CppSourceCompatibility.Cpp14         || "-std=c++14" // c++14 stops being experimental at 5.0
        '5.0'   | CppSourceCompatibility.Cpp14Extended || "-std=gnu++14"
        '6.3'   | CppSourceCompatibility.Cpp14         || "-std=c++14"
        '6.3'   | CppSourceCompatibility.Cpp14Extended || "-std=gnu++14"

        '5.0'   | CppSourceCompatibility.Cpp17         || "-std=c++1z"
        '5.0'   | CppSourceCompatibility.Cpp17Extended || "-std=gnu++1z"
        '6.3'   | CppSourceCompatibility.Cpp17         || "-std=c++1z"
        '6.3'   | CppSourceCompatibility.Cpp17Extended || "-std=gnu++1z"
        '8.0'   | CppSourceCompatibility.Cpp17         || "-std=c++17" // c++17 stops being experimental at 8.0
        '8.0'   | CppSourceCompatibility.Cpp17Extended || "-std=gnu++17"

        '8.0'   | CppSourceCompatibility.Cpp2a         || "-std=c++2a"
        '8.0'   | CppSourceCompatibility.Cpp2aExtended || "-std=gnu++2a"
        '9.0'   | CppSourceCompatibility.Cpp2a         || "-std=c++2a"
        '9.0'   | CppSourceCompatibility.Cpp2aExtended || "-std=gnu++2a"
    }
}
