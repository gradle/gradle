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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId

class ComponentModuleMetadataContainerTest extends Specification {
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock() {
        module(_, _) >> { args ->
            DefaultModuleIdentifier.newId(*args)
        }
    }

    @Subject replacements = new ComponentModuleMetadataContainer(moduleIdentifierFactory)

    def "keeps track of replacements"() {
        replacements.module("com.google.collections:google-collections").replacedBy("com.google.guava:guava");
        replacements.module(newId("foo", "bar")).replacedBy(newId("foo", "xxx"), 'custom');

        expect:
        replacements.getReplacementFor(newId("com.google.collections", "google-collections")).target == newId("com.google.guava", "guava")
        replacements.getReplacementFor(newId("foo", "bar")).target == newId("foo", "xxx")
        replacements.getReplacementFor(newId("foo", "bar")).reason == 'custom'

        !replacements.getReplacementFor(newId("com.google.guava", "guava"))
        !replacements.getReplacementFor(newId("bar", "foo"))
    }

    def "does not allow replacing with the same module"() {
        when: replacements.module("o:o").replacedBy("o:o")
        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot declare module replacement that replaces self: o:o->o:o"
    }

    def "detects cycles early"() {
        replacements.module("o:a").replacedBy("o:b")
        when: replacements.module("o:b").replacedBy("o:a")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot declare module replacement o:b->o:a because it introduces a cycle: o:b->o:a->o:b"
    }

    def "detects transitive cycles early"() {
        replacements.module("o:o").replacedBy("o:x")
        //a->b->c->a
        replacements.module("o:a").replacedBy("o:b")
        replacements.module("o:b").replacedBy("o:c")
        when: replacements.module("o:c").replacedBy("o:a")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot declare module replacement o:c->o:a because it introduces a cycle: o:c->o:a->o:b->o:c"
    }

    def "provides module metadata information"() {
        def module = replacements.module("com.google.collections:google-collections")

        expect:
        module.id == newId("com.google.collections", "google-collections")
        module.replacedBy == null

        when: module.replacedBy("a:b")
        then: module.replacedBy == newId("a", "b")
    }
}
