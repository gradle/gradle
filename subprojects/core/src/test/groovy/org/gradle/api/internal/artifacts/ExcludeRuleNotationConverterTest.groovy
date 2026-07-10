/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.util.internal.WrapUtil
import spock.lang.Specification

class ExcludeRuleNotationConverterTest extends Specification {
    def parser = NotationParserBuilder.toType(ExcludeRule).converter(new ExcludeRuleNotationConverter()).toComposite()

    def "with group"() {
        when:
        ExcludeRule d = parser.parseNotation([group: 'aGroup']);
        then:
        d != null;
        d instanceof DefaultExcludeRule
        d.group == 'aGroup'
        !d.module
    }

    def "with module"() {
        when:
        ExcludeRule d = parser.parseNotation([module: 'aModule']);
        then:
        d != null;
        d instanceof DefaultExcludeRule
        d.module == 'aModule'
        !d.group
    }

    def "with group and module"() {
        when:
        ExcludeRule d = parser.parseNotation([group: 'aGroup', module: 'aModule']);
        then:
        d != null;
        d instanceof DefaultExcludeRule
        d.group == 'aGroup'
        d.module == 'aModule'
    }

    def "with no group and no module InvalidUserDataException is thrown"() {
        when:
        parser.parseNotation([invalidKey1: 'aGroup', invalidKey2: 'aModule']);
        then:
        thrown(InvalidUserDataException)
    }

    def "throw exception with a valid group but invalid second key"() {
        when:
        parser.parseNotation([group: 'aGroup', invalidKey1:"invalidValue"]);
        then:
        thrown(MissingPropertyException)
    }

    def "checkValidExcludeRuleMap is true if group or module is defined"() {
        expect:
        parser.parseNotation(WrapUtil.toMap(ExcludeRule.GROUP_KEY, "aGroup"));
        parser.parseNotation(WrapUtil.toMap(ExcludeRule.MODULE_KEY, "aModule"));

        when:
        parser.parseNotation(WrapUtil.toMap("unknownKey", "someValue"))

        then:
        thrown(InvalidUserDataException)
    }
}
