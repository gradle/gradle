/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core

import spock.lang.Specification

class ModelPathValidationTest extends Specification {

    def "validate name"() {
        when:
        ModelPath.validateName("")

        then:
        def e = thrown(ModelPath.InvalidNameException)
        e.message =~ "empty string"

        when:
        ModelPath.validateName("ü")

        then:
        e = thrown(ModelPath.InvalidNameException)
        e.message =~ "first character"

        when:
        ModelPath.validateName(" ")

        then:
        e = thrown(ModelPath.InvalidNameException)
        e.message =~ "first character"

        when:
        ModelPath.validateName("a ")

        then:
        e = thrown(ModelPath.InvalidNameException)
        e.message =~ "contains illegal"

        when:
        ModelPath.validateName("aü")

        then:
        e = thrown(ModelPath.InvalidNameException)
        e.message =~ "contains illegal"

        when:
        ModelPath.validateName("abü")

        then:
        e = thrown(ModelPath.InvalidNameException)
        e.message =~ "contains illegal"

        when:
        ModelPath.validateName("Z9z")
        ModelPath.validateName("abc")
        ModelPath.validateName("aBC")
        ModelPath.validateName("a10")
        ModelPath.validateName("_")
        ModelPath.validateName("_a")
        ModelPath.validateName("__")
        ModelPath.validateName("a_Z")

        then:
        noExceptionThrown()
    }

    def "validate model path"() {
        when:
        ModelPath.validatePath("foo. bar")

        then:
        def e = thrown ModelPath.InvalidPathException
        e.cause instanceof ModelPath.InvalidNameException

        when:
        ModelPath.validatePath("foo.bar")

        then:
        noExceptionThrown()

        when:
        ModelPath.validatePath(".foo.bar")

        then:
        e = thrown ModelPath.InvalidPathException
        e.message =~ "start with"

        when:
        ModelPath.validatePath("foo.bar.")

        then:
        e = thrown ModelPath.InvalidPathException
        e.message =~ "end with"

        when:
        ModelPath.validatePath("")

        then:
        e = thrown ModelPath.InvalidPathException
        e.message =~ "empty string"

        when:
        ModelPath.validatePath("-")

        then:
        e = thrown ModelPath.InvalidNameException
        e.message =~ "illegal first character '-'"

    }
}
