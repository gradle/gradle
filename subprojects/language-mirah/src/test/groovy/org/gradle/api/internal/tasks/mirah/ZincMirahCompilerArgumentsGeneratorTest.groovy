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

package org.gradle.api.internal.tasks.mirah

import org.gradle.language.mirah.tasks.BaseMirahCompileOptions
import spock.lang.Specification

class ZincMirahCompilerArgumentsGeneratorTest extends Specification {
    def generator = new ZincMirahCompilerArgumentsGenerator()
    def spec = new DefaultMirahCompileSpec()

    def setup() {
        spec.setMirahCompileOptions(new BaseMirahCompileOptions())
    }

    def "default options"() {
        expect:
        generator.generate(spec) as Set == ["-deprecation", "-unchecked"] as Set
    }

    def "can suppress deprecation flag"() {
        spec.mirahCompileOptions.deprecation = false

        expect:
        !generator.generate(spec).contains("-deprecation")
    }

    def "can suppress unchecked flag"() {
        spec.mirahCompileOptions.unchecked = false

        expect:
        !generator.generate(spec).contains("-unchecked")
    }

    def "generates debug level option"() {
        spec.mirahCompileOptions.debugLevel = "someLevel"

        expect:
        generator.generate(spec).contains("-g:someLevel")
    }

    def "generates optimize flag"() {
        spec.mirahCompileOptions.optimize = true

        expect:
        generator.generate(spec).contains("-optimise")
    }

    def "generates encoding option"() {
        spec.mirahCompileOptions.encoding = "some encoding"

        when:
        def args = generator.generate(spec)

        then:
        args.contains("-encoding")
        args.contains("some encoding")
    }

    def "generates verbose flag"() {
        spec.mirahCompileOptions.debugLevel = "verbose"

        expect:
        generator.generate(spec).contains("-verbose")
    }

    def "generates debug flag"() {
        spec.mirahCompileOptions.debugLevel = "debug"

        expect:
        generator.generate(spec).contains("-Ydebug")
    }

    def "generates logging phases options"() {
        spec.mirahCompileOptions.loggingPhases = ["foo", "bar", "baz"]

        when:
        def args = generator.generate(spec)

        then:
        args.contains("-Ylog:foo")
        args.contains("-Ylog:bar")
        args.contains("-Ylog:baz")
    }

    def "adds any additional parameters"() {
        spec.mirahCompileOptions.additionalParameters = ["-other", "value"]

        when:
        def args = generator.generate(spec)

        then:
        args.contains("-other")
        args.contains("value")
    }
}
