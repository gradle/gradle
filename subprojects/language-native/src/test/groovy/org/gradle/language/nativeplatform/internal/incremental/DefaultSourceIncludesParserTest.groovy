/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental

import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.TestIncludeParser
import spock.lang.Specification

class DefaultSourceIncludesParserTest extends Specification {
    def sourceParser = Mock(CSourceParser)
    def sourceIncludes = Mock(IncludeDirectives)

    def "returns a filtered SourceIncludes when not importAware"() {
        given:
        def file = new File("test")
        def noImports = Mock(IncludeDirectives)

        when:
        def includesParser = new DefaultSourceIncludesParser(sourceParser, false)

        1 * sourceParser.parseSource(file) >> sourceIncludes
        1 * sourceIncludes.discardImports() >> noImports
        0 * sourceIncludes._

        and:
        def includes = includesParser.parseIncludes(file)

        then:
        includes.is(noImports)
    }

    def "returns the parsed SourceIncludes when importAware"() {
        given:
        def file = new File("test")

        when:
        def includesParser = new DefaultSourceIncludesParser(sourceParser, true)

        1 * sourceParser.parseSource(file) >> sourceIncludes
        0 * sourceIncludes._

        and:
        def includes = includesParser.parseIncludes(file)

        then:
        includes.is(sourceIncludes)
    }

    Include include(String value, boolean isImport = false) {
        return TestIncludeParser.parse(value, isImport)
    }
}
