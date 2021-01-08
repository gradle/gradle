/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification
import spock.lang.Subject

class ModuleSelectorStringNotationConverterTest extends Specification {
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock() {
        module(_, _) >> { args ->
            DefaultModuleIdentifier.newId(*args)
        }
    }

    @Subject parser = NotationParserBuilder.toType(ComponentSelector).converter(new ModuleSelectorStringNotationConverter(moduleIdentifierFactory)).toComposite()

    def "parses module identifier notation"() {
        expect:
        parser.parseNotation("org.gradle:gradle-core") == new UnversionedModuleComponentSelector(moduleIdentifierFactory.module("org.gradle", "gradle-core"))
        parser.parseNotation(" foo:bar ") == new UnversionedModuleComponentSelector(moduleIdentifierFactory.module("foo", "bar"))
    }

    def "parses module component identifier notation"() {
        expect:
        parser.parseNotation("org.gradle:gradle-core:1.+") == DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "gradle-core"), new DefaultMutableVersionConstraint("1.+"))
        parser.parseNotation(" foo:bar:[1.3, 2.0)") == DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("foo", "bar"), new DefaultMutableVersionConstraint("[1.3, 2.0)"))
    }

    def "reports invalid notation"() {
        when: parser.parseNotation(notation)
        then: thrown(UnsupportedNotationException)
        where: notation << [null, "", ":", "foo", "foo:", "foo:bar:x:2", "  :", ":  ", "  :  "]
    }

    def "reports notation with invalid character for module"() {
        when: parser.parseNotation("group:module${character}")
        then: thrown(UnsupportedNotationException)
        where: character << ["+", "*", "[", "]", "(", ")", ","]
    }

    def "reports notation with invalid character for module component"() {
        when: parser.parseNotation("group:module${character}:1.0")
        then: thrown(UnsupportedNotationException)
        where: character << ["+", "*", "[", "]", "(", ")", ","]
    }
}
