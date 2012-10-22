/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.scala

import spock.lang.Specification

class ScalaCompilerArgumentsGeneratorTest extends Specification {
    def generator = new ScalaCompilerArgumentsGenerator()
    def spec = new DefaultScalaCompileSpec()

    def "generates no options for empty spec"() {
        expect:
        generator.generate(spec) == []
    }

    def "generates encoding option"() {
        spec.scalaCompileOptions.encoding = "some encoding"

        expect:
        generator.generate(spec) == ["-encoding", "some encoding"]
    }

    def "generates debug level option"() {
        spec.scalaCompileOptions.debugLevel = "someLevel"

        expect:
        generator.generate(spec) == ["-g:someLevel"]
    }
}
