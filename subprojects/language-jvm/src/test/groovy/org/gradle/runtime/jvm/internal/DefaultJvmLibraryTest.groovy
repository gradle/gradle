/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.jvm.internal

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import spock.lang.Specification

class DefaultJvmLibraryTest extends Specification {
    def "library has name"() {
        when:
        def library = new DefaultJvmLibrary("jvm-lib")

        then:
        library.name == "jvm-lib"
    }

    def "converts FunctionalSourceSet to LanguageSourceSets"() {
        given:
        def functionalSourceSet = new DefaultFunctionalSourceSet("fss", new DirectInstantiator())
        def languageSourceSet1 = languageSourceSet("lss1")
        def languageSourceSet2 = languageSourceSet("lss2")
        functionalSourceSet.add(languageSourceSet1)
        functionalSourceSet.add(languageSourceSet2)

        when:
        def library = new DefaultJvmLibrary("jvm-lib")
        library.source functionalSourceSet

        then:
        library.source as List == [languageSourceSet1, languageSourceSet2]
    }

    def "flattens Collections of LanguageSourceSets"() {
        given:
        def languageSourceSet1 = languageSourceSet("lss1")
        def languageSourceSet2 = languageSourceSet("lss2")
        def languageSourceSet3 = languageSourceSet("lss1")
        def languageSourceSet4 = languageSourceSet("lss2")

        when:
        def library = new DefaultJvmLibrary("jvm-lib")
        library.source([languageSourceSet1, languageSourceSet2])
        library.source([languageSourceSet3, languageSourceSet4])

        then:
        library.source as List == [languageSourceSet1, languageSourceSet2, languageSourceSet3, languageSourceSet4]
    }

    private LanguageSourceSet languageSourceSet(def name) {
        Mock(LanguageSourceSet) {
            getName() >> name
        }
    }
}
