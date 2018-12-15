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
    def "throw IAE when clang #version requests source compatibility #srcCompatibility"() {
        VersionNumber versionNumber = VersionNumber.parse(version)

        when:
        ClangVersionCppSourceCompatibilitySupport.getSourceCompatibilityOption(versionNumber, srcCompatibility)

        then:
        thrown(IllegalArgumentException)

        where:
        version | srcCompatibility
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
}
