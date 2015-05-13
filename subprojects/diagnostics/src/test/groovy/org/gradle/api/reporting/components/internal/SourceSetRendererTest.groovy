/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.components.internal

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.language.base.LanguageSourceSet
import org.gradle.logging.StyledTextOutput
import spock.lang.Specification

class SourceSetRendererTest extends Specification {

    SourceSetRenderer renderer = new SourceSetRenderer()
    LanguageSourceSet languageSourceSet = Mock()
    TextReportBuilder reportBuilder = Mock()
    StyledTextOutput styledTextOutput = Mock()
    SourceDirectorySet sourceDirectorySet = Mock()

    File srcFolder1
    File srcFolder2

    def setup() {
        _ * languageSourceSet.displayName >> "acme:sample"
        _ * languageSourceSet.source >> sourceDirectorySet
        _ * reportBuilder.getOutput() >> styledTextOutput
        1 * styledTextOutput.println("Acme:sample")
    }

    def "shows sourceSet folders"() {
        given:
        withTwoSourceFolders()
        1 * sourceDirectorySet.getIncludes() >> []
        1 * sourceDirectorySet.getExcludes() >> []
        when:
        renderer.render(languageSourceSet, reportBuilder)
        then:
        1 * reportBuilder.item("srcDir", srcFolder1)
        1 * reportBuilder.item("srcDir", srcFolder2)

    }

    def "shows includes / excludes"() {
        given:
        withTwoSourceFolders()
        1 * sourceDirectorySet.getIncludes() >> includes
        1 * sourceDirectorySet.getExcludes() >> excludes
        when:
        renderer.render(languageSourceSet, reportBuilder)
        then:
        expectedOutputs.each { key, value ->
            1 * reportBuilder.item(key, value)
        }
        where:
        includes                    | excludes                   | expectedOutputs
        ["**/*.java"]               | ["**/gen/**"]              | [includes: "**/*.java", excludes: "**/gen/**"]
        ["**/*.java", "**/*.scala"] | ["**/gen/**"]              | [includes: "**/*.java, **/*.scala", excludes: "**/gen/**"]
        ["**/*.scala"]              | ["**/gen/**", "**/*.java"] | [includes: "**/*.scala", excludes: "**/gen/**, **/*.java"]
    }

    def withTwoSourceFolders() {
        srcFolder1 = new File("src/folder1")
        srcFolder2 = new File("src/folder2")
        1 * sourceDirectorySet.srcDirs >> [srcFolder1, srcFolder2]
    }
}
