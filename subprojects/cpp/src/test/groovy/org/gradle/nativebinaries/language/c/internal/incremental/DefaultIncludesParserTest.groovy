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

package org.gradle.nativebinaries.language.c.internal.incremental

import org.gradle.nativebinaries.language.c.internal.incremental.sourceparser.CSourceParser
import spock.lang.Specification

class DefaultIncludesParserTest extends Specification {
    def sourceParser = Mock(CSourceParser)
    def sourceDetails = Mock(CSourceParser.SourceDetails)

    def "imports are not included in includes"() {
        given:
        def file = new File("test")

        when:
        def includesParser = new DefaultIncludesParser(sourceParser, false)

        1 * sourceParser.parseSource(file) >> sourceDetails
        1 * sourceDetails.includes >> ['"quoted"', '<system>']
        0 * sourceDetails._

        and:
        def includes = includesParser.parseIncludes(file)

        then:
        includes.quotedIncludes == ["quoted"]
        includes.systemIncludes == ["system"]
    }


    def "imports are included in includes"() {
        given:
        def file = new File("test")

        when:
        def includesParser = new DefaultIncludesParser(sourceParser, true)

        1 * sourceParser.parseSource(file) >> sourceDetails
        1 * sourceDetails.includes >> ['"quoted"', '<system>']
        1 * sourceDetails.imports >> ['"quotedImport"', '<systemImport>']
        0 * sourceDetails._

        and:
        def includes = includesParser.parseIncludes(file)

        then:
        includes.quotedIncludes == ["quoted", "quotedImport"]
        includes.systemIncludes == ["system", "systemImport"]
    }

}
