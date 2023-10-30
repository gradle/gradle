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

package org.gradle.model.internal.registry

import org.gradle.model.internal.core.ModelPath
import spock.lang.Specification

class ModelPathSuggestionProviderTest extends Specification {

    List<String> availablePaths

    List<String> suggestionsFor(String path) {
        new ModelPathSuggestionProvider(availablePaths.collect { new ModelPath(it) }).transform(new ModelPath(path))*.toString()
    }

    def "suggests model paths with Levenshtein distance lower than 4"() {
        when:
        availablePaths = ["task.afoobar", "tasks.boofar", "tasks.foobar", "tasks.f", "fooba"]

        then:
        suggestionsFor("tasks.fooba") == ["tasks.foobar", "task.afoobar", "tasks.boofar"]
    }

    def "suggests model paths with Levenshtein distance lower than half it's length for strings shorter than 6 characters"() {
        when:
        availablePaths = ["fo", "bor", "for", "of", "foob"]

        then:
        suggestionsFor("foo") == ["fo", "foob", "for"]
        suggestionsFor("foor") == ["foob", "for", "bor", "fo"]
        suggestionsFor("foora") == ["foob", "for"]
    }

    def "suggestions are ordered by distance then alphabetical"() {
        when:
        availablePaths = ["tasks.foobar", "task.afoobar", "tasks.f", "tasks.boofar", "fooba", "tasks.foo"]

        then:
        suggestionsFor("tasks.fooba") == ["tasks.foobar", "tasks.foo", "task.afoobar", "tasks.boofar"]
    }
}
