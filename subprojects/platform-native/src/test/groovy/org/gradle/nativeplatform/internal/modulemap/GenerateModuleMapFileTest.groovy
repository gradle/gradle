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

package org.gradle.nativeplatform.internal.modulemap

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.nativeplatform.internal.modulemap.GenerateModuleMapFile.generateFile
import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

class GenerateModuleMapFileTest extends Specification {
    @Rule TemporaryFolder tempDir = new TemporaryFolder()

    def "can generate a simple module map file"() {
        def moduleMapFile = tempDir.newFile("module.modulemap")
        def headers = tempDir.newFolder('headers')
        def moreHeaders = tempDir.newFolder('moreHeaders')

        when:
        generateFile(moduleMapFile, "foo", [headers.absolutePath, moreHeaders.absolutePath])

        then:
        normaliseLineSeparators(moduleMapFile.text) == """module foo {
\tumbrella "${headers.absolutePath}"
\tumbrella "${moreHeaders.absolutePath}"
\texport *
}
"""
    }

    def "does not include non-existent directories"() {
        def moduleMapFile = tempDir.newFile("module.modulemap")
        def headers = tempDir.newFolder('headers')
        def moreHeaders = tempDir.newFolder('moreHeaders')

        when:
        generateFile(moduleMapFile, "foo", [headers.absolutePath, moreHeaders.absolutePath, new File('does-not-exist').absolutePath])

        then:
        normaliseLineSeparators(moduleMapFile.text) == """module foo {
\tumbrella "${headers.absolutePath}"
\tumbrella "${moreHeaders.absolutePath}"
\texport *
}
"""
    }

    def "creates parent directory if necessary"() {
        def moduleMapFile = new File(tempDir.root, "maps/module.modulemap")
        def headers = tempDir.newFolder('headers')
        def moreHeaders = tempDir.newFolder('moreHeaders')

        given:
        assert !moduleMapFile.parentFile.exists()

        when:
        generateFile(moduleMapFile, "foo", [headers.absolutePath, moreHeaders.absolutePath])

        then:
        normaliseLineSeparators(moduleMapFile.text) == """module foo {
\tumbrella "${headers.absolutePath}"
\tumbrella "${moreHeaders.absolutePath}"
\texport *
}
"""
    }
}
