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



package org.gradle.api.internal.notations

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId

class ModuleIdentifierNotationConverterTest extends Specification {
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock() {
        module(_, _) >> { args ->
            DefaultModuleIdentifier.newId(*args)
        }
    }
    @Subject parser = NotationParserBuilder.toType(ModuleIdentifier).fromCharSequence(new ModuleIdentifierNotationConverter(moduleIdentifierFactory)).toComposite()

    def "parses module identifier notation"() {
        expect:
        parser.parseNotation("org.gradle:gradle-core") == newId("org.gradle", "gradle-core")
        parser.parseNotation(" foo:bar ") == newId("foo", "bar")
    }

    def "reports invalid notation"() {
        when: parser.parseNotation(notation)
        then: thrown(UnsupportedNotationException)
        where: notation << [null, "", ":", "foo:", "bar:", "foo:bar:baz", "  :", ":  ", "  :  "]
    }

    def "reports notation with invalid character"() {
        when: parser.parseNotation("group:module${character}")
        then: thrown(UnsupportedNotationException)
        where: character << ["*", "[", "]", "(", ")", ","]
    }


}
