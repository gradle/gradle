/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.scala.compile.internal

import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

@Subject(ScalaCompileOptions)
class ScalaCompileOptionsTest extends Specification {

    def 'additional parameters are empty by default'() {
        ScalaCompileOptions scalaCompileOptions = TestUtil.newInstance(ScalaCompileOptions)

        expect:
        scalaCompileOptions.additionalParameters != null
        scalaCompileOptions.additionalParameters.get().isEmpty()
    }

    def 'can append to additional parameters #description'() {
        ScalaCompileOptions scalaCompileOptions = TestUtil.newInstance(ScalaCompileOptions)

        when:
        action(scalaCompileOptions)
        scalaCompileOptions.additionalParameters.add("-some-flag")

        then:
        scalaCompileOptions.additionalParameters.get() == ["-some-flag"]

        where:
        description                       | action
        "by default"                      | {}
        "after unsetting a property"      | { it.additionalParameters.unset() }
        "after setting to immutable list" | { it.additionalParameters = [].asImmutable() }
    }
}
