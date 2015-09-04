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
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.diagnostics.internal.text.DefaultTextReportBuilder
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DependentSourceSetInternal
import org.gradle.logging.TestStyledTextOutput
import org.gradle.platform.base.DependencySpecContainer
import org.gradle.platform.base.internal.DefaultDependencySpecContainer
import spock.lang.Specification

class SourceSetRendererTest extends Specification {

    SourceSetRenderer renderer = new SourceSetRenderer()
    LanguageSourceSet languageSourceSet = Mock(LanguageSourceSet)
    SourceDirectorySet sourceDirectorySet = Mock(SourceDirectorySet)

    def resolver = Stub(FileResolver) {
        resolveAsRelativePath(_) >> { return it[0].toString() }
    }
    def output = new TestStyledTextOutput()
    def builder = new DefaultTextReportBuilder(output, resolver)

    File srcFolder1 = new File("src/folder1")
    File srcFolder2 = new File("src/folder2")

    def setup() {
        _ * languageSourceSet.displayName >> "acme:sample"
        _ * languageSourceSet.source >> sourceDirectorySet
        _ * sourceDirectorySet.srcDirs >> [srcFolder1, srcFolder2]
    }

    def "shows sourceSet folders"() {
        given:
        _ * sourceDirectorySet.getIncludes() >> []
        _ * sourceDirectorySet.getExcludes() >> []

        when:
        renderer.render(languageSourceSet, builder)

        then:
        output.value.startsWith("Acme:sample")
        output.value.contains("srcDir: " + srcFolder1)
        output.value.contains("srcDir: " + srcFolder2)

    }

    def "shows includes / excludes"() {
        given:
        1 * sourceDirectorySet.getIncludes() >> includes
        1 * sourceDirectorySet.getExcludes() >> excludes

        when:
        renderer.render(languageSourceSet, builder)

        then:
        expectedOutputs.each {
            output.value.contains it
        }
        where:
        includes                    | excludes                   | expectedOutputs
        ["**/*.java"]               | ["**/gen/**"]              | ["includes: **/*.java", "excludes: **/gen/**"]
        ["**/*.java", "**/*.scala"] | ["**/gen/**"]              | ["includes: **/*.java, **/*.scala", "excludes: **/gen/**"]
        ["**/*.scala"]              | ["**/gen/**", "**/*.java"] | ["includes: **/*.scala", "excludes: **/gen/**, **/*.java"]
    }

    def "shows dependencies"() {
        DependencySpecContainer dsc = new DefaultDependencySpecContainer()
        dsc.project("a-project")
        dsc.library("a-library")
        dsc.project("some-project").library("some-library")

        given:
        def dependentSourceSet = Mock(DependentLanguageSourceSet)
        dependentSourceSet.dependencies >> dsc

        _ * dependentSourceSet.displayName >> "acme:sample"
        _ * dependentSourceSet.source >> sourceDirectorySet
        _ * sourceDirectorySet.getIncludes() >> []
        _ * sourceDirectorySet.getExcludes() >> []

        when:
        renderer.render(dependentSourceSet, builder)

        then:
        output.value.contains("""
    dependencies
        project 'a-project'
        library 'a-library'
        project 'some-project' library 'some-library'
""")
    }

    interface DependentLanguageSourceSet extends LanguageSourceSet, DependentSourceSetInternal {}
}
