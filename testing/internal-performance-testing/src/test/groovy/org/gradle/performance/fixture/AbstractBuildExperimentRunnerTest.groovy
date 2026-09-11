/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.performance.fixture

import joptsimple.OptionParser
import org.gradle.profiler.ProfilerFactory
import spock.lang.Specification

/* TODO(humanize) PROBE SUPPORT, NOT FOR MERGE. Guards AbstractBuildExperimentRunner.profilerOptions.
   Every async-profiler option is registered availableIf("profile"), so passing one without also
   passing --profile throws UnavailableOptionException at test setup, before any build runs. I found
   that out by burning a CI run, hence this test. */
class AbstractBuildExperimentRunnerTest extends Specification {

    def "profiler options parse for #profilerName"() {
        given:
        def parser = new OptionParser()
        parser.accepts("profiler")
        ProfilerFactory.configureParser(parser)

        when:
        parser.parse(AbstractBuildExperimentRunner.profilerOptions(profilerName))

        then:
        noExceptionThrown()

        where:
        profilerName << ["async-profiler", "async-profiler-wall", "async-profiler-all", "jfr", "none"]
    }

    def "async-profiler options request a finer interval than the gradle-profiler default"() {
        when:
        def options = AbstractBuildExperimentRunner.profilerOptions("async-profiler-wall").toList()

        then:
        options.contains("--async-profiler-wall-interval")
        options[options.indexOf("--async-profiler-wall-interval") + 1] == "1000000"
    }

    def "explicitly configured options win over the defaults"() {
        given:
        System.setProperty("org.gradle.performance.profiler.options", "  --profile async-profiler   --async-profiler-interval 5000000  ")

        when:
        def options = AbstractBuildExperimentRunner.profilerOptions("async-profiler").toList()

        then:
        options == ["--profile", "async-profiler", "--async-profiler-interval", "5000000"]

        cleanup:
        System.clearProperty("org.gradle.performance.profiler.options")
    }
}
