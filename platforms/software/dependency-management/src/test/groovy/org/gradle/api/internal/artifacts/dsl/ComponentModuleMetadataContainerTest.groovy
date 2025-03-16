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

import static org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId

class ComponentModuleMetadataContainerTest extends Specification {
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock() {
        module(_, _) >> { args ->
            DefaultModuleIdentifier.newId(*args)
        }
    }

    ComponentModuleMetadataContainer modules = new ComponentModuleMetadataContainer(moduleIdentifierFactory)

    def "keeps track of replacements"() {
        modules.module("com.google.collections:google-collections").replacedBy("com.google.guava:guava");
        modules.module(newId("foo", "bar")).replacedBy(newId("foo", "xxx"), 'custom');

        expect:
        modules.replacements.getReplacementFor(newId("com.google.collections", "google-collections")).target == newId("com.google.guava", "guava")
        modules.replacements.getReplacementFor(newId("foo", "bar")).target == newId("foo", "xxx")
        modules.replacements.getReplacementFor(newId("foo", "bar")).reason == 'custom'

        !modules.replacements.getReplacementFor(newId("com.google.guava", "guava"))
        !modules.replacements.getReplacementFor(newId("bar", "foo"))
    }

    def "does not allow replacing with the same module"() {
        when:
        modules.module("o:o").replacedBy("o:o")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot declare module replacement that replaces self: o:o->o:o"
    }

    def "detects cycles early"() {
        when:
        modules.module("o:a").replacedBy("o:b")
        modules.module("o:b").replacedBy("o:a")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot declare module replacement o:b->o:a because it introduces a cycle: o:b->o:a->o:b"
    }

    def "detects transitive cycles early"() {
        when:
        modules.module("o:o").replacedBy("o:x")
        //a->b->c->a
        modules.module("o:a").replacedBy("o:b")
        modules.module("o:b").replacedBy("o:c")
        modules.module("o:c").replacedBy("o:a")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot declare module replacement o:c->o:a because it introduces a cycle: o:c->o:a->o:b->o:c"
    }

    def "provides module metadata information"() {
        def module = modules.module("com.google.collections:google-collections")

        expect:
        module.id == newId("com.google.collections", "google-collections")
        module.replacedBy == null

        when:
        module.replacedBy("a:b")

        then:
        module.replacedBy == newId("a", "b")
    }
}
