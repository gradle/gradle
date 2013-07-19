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
package org.gradle.nativecode.base.internal
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import spock.lang.Specification

class SourceSetNotationParserTest extends Specification {
    def parser = SourceSetNotationParser.parser()
    def languageSourceSet1 = languageSourceSet("lss1")
    def languageSourceSet2 = languageSourceSet("lss2")

    def "translates single LanguageSourceSet"() {
        expect:
        parser.parseNotation(languageSourceSet1) == [languageSourceSet1] as Set
    }

    def "collects all LanguageSourceSets for a FunctionalSourceSet"() {
        when:
        def functionalSourceSet = new DefaultFunctionalSourceSet("func", new DirectInstantiator())
        functionalSourceSet.add(languageSourceSet1)
        functionalSourceSet.add(languageSourceSet2)

        then:
        parser.parseNotation(functionalSourceSet) == [languageSourceSet1, languageSourceSet2] as Set
    }

    def "collects all LanguageSourceSets in a collection"() {
        expect:
        parser.parseNotation([languageSourceSet1, languageSourceSet2]) == [languageSourceSet1, languageSourceSet2] as Set
    }

    private LanguageSourceSet languageSourceSet(def name) {
        Stub(LanguageSourceSet) {
            getName() >> name
        }
    }
}
