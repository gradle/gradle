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

package org.gradle.api.internal.artifacts.dsl

import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId

class ModuleReplacementsTest extends Specification {

    @Subject replacements = new ModuleReplacements()

    def "keeps track of replacements"() {
        replacements.module("com.google.collections:google-collections").replacedBy("com.google.guava:guava");
        expect:
        replacements.getReplacementFor(newId("com.google.collections", "google-collections")) == newId("com.google.guava", "guava")
        !replacements.getReplacementFor(newId("com.google.guava", "guava"))
    }
}
