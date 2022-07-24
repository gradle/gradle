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

package org.gradle.language.nativeplatform.internal

import spock.lang.Specification

class NamesTest extends Specification {
    def "names for main"() {
        expect:
        def name = Names.of("main")
        name.baseName == "main"
        name.lowerBaseName == "main"
        name.getCompileTaskName("cpp") == "compileCpp"
        name.getTaskName("link") == "link"
        name.dirName == "main/"
        name.withPrefix("compile") == "compile"
        name.withSuffix("implementation") == "implementation"

        name.append("debug").dirName == "main/debug/"
    }

    def "names for single dimension variant of main"() {
        expect:
        def name = Names.of("mainDebug")
        name.baseName == "debug"
        name.lowerBaseName == "debug"
        name.getCompileTaskName("cpp") == "compileDebugCpp"
        name.getTaskName("link") == "linkDebug"
        name.dirName == "main/debug/"
        name.withPrefix("compile") == "compileDebug"
        name.withSuffix("implementation") == "debugImplementation"

        name.append("shared").dirName == "main/debug/shared/"
    }

    def "names for multi-dimension variant of main"() {
        expect:
        def name = Names.of("mainDebugStatic")
        name.baseName == "debugStatic"
        name.lowerBaseName == "debug-static"
        name.getCompileTaskName("cpp") == "compileDebugStaticCpp"
        name.getTaskName("link") == "linkDebugStatic"
        name.dirName == "main/debug/static/"
        name.withPrefix("compile") == "compileDebugStatic"
        name.withSuffix("implementation") == "debugStaticImplementation"

        name.append("cpp17").dirName == "main/debug/static/cpp17/"
    }

    def "names for custom"() {
        expect:
        def name = Names.of("custom")
        name.baseName == "custom"
        name.lowerBaseName == "custom"
        name.getCompileTaskName("cpp") == "compileCustomCpp"
        name.getTaskName("link") == "linkCustom"
        name.dirName == "custom/"
        name.withPrefix("compile") == "compileCustom"
        name.withSuffix("implementation") == "customImplementation"

        name.append("debug").dirName == "custom/debug/"
    }

    def "names for single dimension variant of custom"() {
        expect:
        def name = Names.of("customRelease")
        name.baseName == "customRelease"
        name.lowerBaseName == "custom-release"
        name.getCompileTaskName("cpp") == "compileCustomReleaseCpp"
        name.getTaskName("link") == "linkCustomRelease"
        name.dirName == "custom/release/"
        name.withPrefix("compile") == "compileCustomRelease"
        name.withSuffix("implementation") == "customReleaseImplementation"

        name.append("static").dirName == "custom/release/static/"
    }

    def "names for multi-dimension variant of custom"() {
        expect:
        def name = Names.of("customReleaseStatic")
        name.baseName == "customReleaseStatic"
        name.lowerBaseName == "custom-release-static"
        name.getCompileTaskName("cpp") == "compileCustomReleaseStaticCpp"
        name.getTaskName("link") == "linkCustomReleaseStatic"
        name.dirName == "custom/release/static/"
        name.withPrefix("compile") == "compileCustomReleaseStatic"
        name.withSuffix("implementation") == "customReleaseStaticImplementation"

        name.append("cpp17").dirName == "custom/release/static/cpp17/"
    }
}
