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

    def "locates value for boolean option"() {
        sysProps["org.gradle.internal.prop1"] = "true"
        sysProps["org.gradle.internal.prop2"] = ""
        sysProps["org.gradle.internal.prop3"] = "false"
        sysProps["org.gradle.internal.prop4"] = "not anything much"

        expect:
        def value1 = options.getOption(new InternalFlag("org.gradle.internal.prop1", false))
        value1.get()
        value1.explicit

        def value2 = options.getOption(new InternalFlag("org.gradle.internal.prop2", false))
        !value2.get()
        value2.explicit

        def value3 = options.getOption(new InternalFlag("org.gradle.internal.prop3", true))
        !value3.get()
        value3.explicit

        def value4 = options.getOption(new InternalFlag("org.gradle.internal.prop4", false))
        !value4.get()
        value4.explicit
    }

    def "uses default for boolean option when system property is not set"() {
        expect:
        def value = options.getOption(new InternalFlag("org.gradle.internal.prop", true))
        value.get()
        !value.explicit
    }

    def "locates value for int option"() {
        sysProps["org.gradle.internal.prop1"] = "12"

        expect:
        def value = options.getOption(new IntegerInternalOption("org.gradle.internal.prop1", 45))
        value.get() == 12
        value.explicit
    }

    def "uses default for int option when system property is not set"() {
        expect:
        def value = options.getOption(new IntegerInternalOption("org.gradle.internal.prop", 23))
        value.get() == 23
        !value.explicit
    }

    def "throws if #option name does not start with expected prefix"() {
        when:
        create("org.gradle.feature.flag")
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Internal property name must start with 'org.gradle.internal.'"

        where:
        option                  | create
        "InternalFlag"          | { String it -> new InternalFlag(it) }
        "IntegerInternalOption" | { String it -> new IntegerInternalOption(it, 0) }
        "StringInternalOption"  | { String it -> new StringInternalOption(it, "") }
    }

    def "throws if internal options does not inherit from a name-validating parent"() {
        when:
        options.getOption(Stub(InternalOption))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Internal option must be an instance of AbstractInternalOption"
    }
}
