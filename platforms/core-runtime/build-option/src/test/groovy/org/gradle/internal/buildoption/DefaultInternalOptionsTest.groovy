/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildoption

import spock.lang.Specification

class DefaultInternalOptionsTest extends Specification {
    def sysProps = [:]
    def options = new DefaultInternalOptions(sysProps)

    def "locates value for boolean option #description"() {
        sysProps["org.gradle.internal.prop1"] = sysProp

        when:
        def value = options.getOptionValue(new InternalFlag("org.gradle.internal.prop1"))

        then:
        value.explicit
        value.get() == result

        where:
        description                    | sysProp         | result
        "for true"                     | "true"          | true
        "case-insensitively for true"  | "TrUe"          | true
        "for false"                    | "false"         | false
        "case-insensitively for false" | "FaLsE"         | false
        "for empty string"             | ""              | false
        "anything else"                | "anything else" | false
    }

    def "uses default for boolean option when system property is not set"() {
        expect:
        def value = options.getOptionValue(new InternalFlag("org.gradle.internal.prop", defaultValue))
        !value.explicit
        value.get() == defaultValue

        where:
        defaultValue << [true, false]
    }

    def "locates value for int option"() {
        sysProps["org.gradle.internal.prop1"] = "12"

        expect:
        def value = options.getOptionValue(new IntegerInternalOption("org.gradle.internal.prop1", 45))
        value.get() == 12
        value.explicit
    }

    def "uses default for int option when system property is not set"() {
        expect:
        def value = options.getOptionValue(new IntegerInternalOption("org.gradle.internal.prop", 23))
        value.get() == 23
        !value.explicit
    }

    def "throws if #option name does not start with expected prefix"() {
        when:
        create("org.gradle.feature.flag")
        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Internal property name must start with 'org.gradle.internal.'")

        when:
        create("org.gradle.internal-feature")
        then:
        e = thrown(IllegalArgumentException)
        e.message.startsWith("Internal property name must start with 'org.gradle.internal.'")

        when:
        create("just.feature")
        then:
        e = thrown(IllegalArgumentException)
        e.message.startsWith("Internal property name must start with 'org.gradle.internal.'")

        where:
        option                  | create
        "InternalFlag"          | { String it -> new InternalFlag(it) }
        "IntegerInternalOption" | { String it -> new IntegerInternalOption(it, 0) }
        "StringInternalOption"  | { String it -> StringInternalOption.of(it, "") }
    }

    //region Convenience accessors: boolean

    def "getBoolean resolves declared option"() {
        sysProps["org.gradle.internal.prop1"] = "true"
        def option = InternalOptions.ofBoolean("org.gradle.internal.prop1", false)

        expect:
        options.getBoolean(option)
    }

    def "getBoolean returns default for declared option when property is not set"() {
        def option = InternalOptions.ofBoolean("org.gradle.internal.prop1", true)

        expect:
        options.getBoolean(option)
    }

    def "getBoolean resolves ad-hoc option"() {
        sysProps["org.gradle.internal.prop1"] = sysProp

        expect:
        options.getBoolean("org.gradle.internal.prop1", false) == result

        where:
        sysProp | result
        "true"  | true
        "false" | false
    }

    def "getBoolean returns default for ad-hoc option when property is not set"() {
        expect:
        options.getBoolean("org.gradle.internal.prop1", defaultValue) == defaultValue

        where:
        defaultValue << [true, false]
    }

    //endregion

    //region Convenience accessors: int

    def "getInt resolves declared option"() {
        sysProps["org.gradle.internal.prop1"] = "42"

        expect:
        options.getInt(InternalOptions.ofInt("org.gradle.internal.prop1", 0)) == 42
    }

    def "getInt returns default for declared option when property is not set"() {
        expect:
        options.getInt(InternalOptions.ofInt("org.gradle.internal.prop1", 99)) == 99
    }

    def "getInt resolves ad-hoc option"() {
        sysProps["org.gradle.internal.prop1"] = "42"

        expect:
        options.getInt("org.gradle.internal.prop1", 0) == 42
    }

    def "getInt returns default for ad-hoc option when property is not set"() {
        expect:
        options.getInt("org.gradle.internal.prop1", 99) == 99
    }

    //endregion

    //region Convenience accessors: string

    def "getString resolves ad-hoc option"() {
        sysProps["org.gradle.internal.prop1"] = "hello"

        expect:
        options.getString("org.gradle.internal.prop1", "default") == "hello"
    }

    def "getString returns default for ad-hoc option when property is not set"() {
        expect:
        options.getString("org.gradle.internal.prop1", "default") == "default"
    }

    def "getStringOrNull resolves set property"() {
        sysProps["org.gradle.internal.prop1"] = "hello"

        expect:
        options.getStringOrNull("org.gradle.internal.prop1") == "hello"
    }

    def "getStringOrNull returns null when property is not set"() {
        expect:
        options.getStringOrNull("org.gradle.internal.prop1") == null
    }

    def "empty string is a valid value for string options"() {
        sysProps["org.gradle.internal.prop1"] = ""

        expect:
        options.getString("org.gradle.internal.prop1", "default") == ""
        options.getStringOrNull("org.gradle.internal.prop1") == ""
    }

    //endregion

    //region getValueOrNull

    def "getValueOrNull resolves value for #optionType option"() {
        sysProps["org.gradle.internal.prop1"] = sysProp

        expect:
        options.getValueOrNull(option) == result

        where:
        optionType | sysProp | option                                                        | result
        "boolean"  | "true"  | InternalOptions.ofBoolean("org.gradle.internal.prop1", false) | true
        "int"      | "42"    | InternalOptions.ofInt("org.gradle.internal.prop1", 0)         | 42
        "string"   | "hello" | InternalOptions.ofString("org.gradle.internal.prop1", "def")  | "hello"
    }

    def "getValueOrNull returns default for #optionType option when property is not set"() {
        expect:
        options.getValueOrNull(option) == result

        where:
        optionType | option                                                       | result
        "boolean"  | InternalOptions.ofBoolean("org.gradle.internal.prop1", true) | true
        "int"      | InternalOptions.ofInt("org.gradle.internal.prop1", 99)       | 99
        "string"   | InternalOptions.ofString("org.gradle.internal.prop1", "def") | "def"
    }

    def "getValueOrNull returns null for nullable string option when property is not set"() {
        expect:
        options.getValueOrNull(InternalOptions.ofStringOrNull("org.gradle.internal.prop1")) == null
    }

    //endregion

    //region isExplicitlySet

    def "isExplicitlySet returns true for explicitly set property"() {
        sysProps["org.gradle.internal.prop1"] = "anything"
        sysProps["org.gradle.internal.prop2"] = ""

        expect:
        options.isExplicitlySet("org.gradle.internal.prop1")
        options.isExplicitlySet("org.gradle.internal.prop2")
    }

    def "isExplicitlySet returns false for unset property"() {
        expect:
        !options.isExplicitlySet("org.gradle.internal.prop1")
    }

    //endregion
}
