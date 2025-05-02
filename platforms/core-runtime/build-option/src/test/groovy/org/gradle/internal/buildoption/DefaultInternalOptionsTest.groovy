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
        sysProps["prop1"] = "true"
        sysProps["prop2"] = ""
        sysProps["prop3"] = "false"
        sysProps["prop4"] = "not anything much"

        expect:
        def value1 = options.getOption(new InternalFlag("prop1", false))
        value1.get()
        value1.explicit

        def value2 = options.getOption(new InternalFlag("prop2", false))
        value2.get()
        value2.explicit

        def value3 = options.getOption(new InternalFlag("prop3", true))
        !value3.get()
        value3.explicit

        def value4 = options.getOption(new InternalFlag("prop4", false))
        value4.get()
        value4.explicit
    }

    def "uses default for boolean option when system property is not set"() {
        expect:
        def value = options.getOption(new InternalFlag("prop", true))
        value.get()
        !value.explicit
    }

    def "locates value for int option"() {
        sysProps["prop1"] = "12"

        expect:
        def value = options.getOption(new IntegerInternalOption("prop1", 45))
        value.get() == 12
        value.explicit
    }

    def "uses default for int option when system property is not set"() {
        expect:
        def value = options.getOption(new IntegerInternalOption("prop", 23))
        value.get() == 23
        !value.explicit
    }
}
