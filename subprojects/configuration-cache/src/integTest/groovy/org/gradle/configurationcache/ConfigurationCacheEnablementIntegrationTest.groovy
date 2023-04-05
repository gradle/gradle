/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

import static org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value.WARN


class ConfigurationCacheEnablementIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    ConfigurationCacheFixture fixture = new ConfigurationCacheFixture(this)

    def "can enable with a command line #origin"() {
        when:
        run 'help', argument

        then:
        fixture.assertStateStored()

        when:
        run 'help', argument

        then:
        fixture.assertStateLoaded()

        where:
        origin            | argument
        "long option"     | ENABLE_CLI_OPT
        "system property" | ENABLE_SYS_PROP
    }

    def "can enable with a property in root directory gradle.properties"() {
        given:
        file('gradle.properties') << """
            $ENABLE_GRADLE_PROP
        """

        when:
        run 'help'

        then:
        fixture.assertStateStored()

        when:
        run 'help'

        then:
        fixture.assertStateLoaded()
    }

    def "can enable with a property in gradle user home gradle.properties"() {
        given:
        executer.requireOwnGradleUserHomeDir()
        executer.gradleUserHomeDir.file('gradle.properties') << """
            $ENABLE_GRADLE_PROP
        """

        when:
        run 'help'

        then:
        fixture.assertStateStored()

        when:
        run 'help'

        then:
        fixture.assertStateLoaded()
    }

    def "can disable with a command line #cliOrigin when enabled in gradle.properties"() {
        given:
        file('gradle.properties') << """
            ${ConfigurationCacheOption.PROPERTY_NAME}=true
        """

        when:
        run 'help', cliArgument

        then:
        fixture.assertNotEnabled()

        where:
        cliOrigin         | cliArgument
        "long option"     | DISABLE_CLI_OPT
        "system property" | DISABLE_SYS_PROP
    }

    def "can enable configuration cache using incubating and final property variants"() {
        given:
        buildFile """
        task check {
            assert gradle.startParameter.configurationCache.get() == ${ccOn}
            assert ${maxProblems == _ } || gradle.startParameter.configurationCacheMaxProblems == ${maxProblems}
            assert ${problemsAs == _ } || gradle.startParameter.configurationCacheProblems.name() == "${problemsAs}"
            assert ${quiet == _ } || gradle.startParameter.configurationCacheQuiet == ${quiet}
            assert ${debug == _ } || gradle.startParameter.configurationCacheDebug == ${debug}
            assert ${recreate == _ } || gradle.startParameter.configurationCacheRecreateCache == ${recreate}
        }
        """

        when:
        run(task, *options)

        then:
        if (ccOn) {
            result.assertHasPostBuildOutput("Configuration cache entry stored.")
        }

        where:
        task    | ccOn  | problemsAs| maxProblems   | quiet | recreate  | debug | options
        "check" | false | WARN      | _             | _     | _         | _     | ["-Dorg.gradle.configuration-cache.problems=warn"]
        "check" | false | _         | 100           | _     | _         | _     | ["-Dorg.gradle.configuration-cache.max-problems=100"]
        "check" | false | _         | _             | true  | _         | _     | ["-Dorg.gradle.configuration-cache.internal.quiet=true"]
        "check" | false | _         | _             | _     | true      | _     | ["-Dorg.gradle.configuration-cache.internal.recreate-cache=true"]
        "check" | false | _         | _             | _     | _         | true  | ["-Dorg.gradle.configuration-cache.internal.debug=true"]
        "check" | false | WARN      | _             | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache-problems=warn"]
        "check" | false | _         | 100           | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache.max-problems=100"]
        "check" | false | _         | _             | true  | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache.quiet=true"]
        "check" | false | _         | _             | _     | true      | _     | ["-Dorg.gradle.unsafe.configuration-cache.recreate-cache=true"]
        "check" | false | _         | _             | _     | _         | true  | ["-Dorg.gradle.unsafe.configuration-cache.debug=true"]
        "check" | true  | _         | _             | _     | _         | _     | ["--configuration-cache"]
        "check" | true  | _         | _             | _     | _         | _     | ["-Dorg.gradle.configuration-cache=true"]
        "check" | true  | _         | _             | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache=true"]
        "check" | false | _         | _             | _     | _         | _     | ["--no-configuration-cache"]
        "check" | false | _         | _             | _     | _         | _     | ["-Dorg.gradle.configuration-cache=false"]
        "check" | false | _         | _             | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache=false"]
    }
}
