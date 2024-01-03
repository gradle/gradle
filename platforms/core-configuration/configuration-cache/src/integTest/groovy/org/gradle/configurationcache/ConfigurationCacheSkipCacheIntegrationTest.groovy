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

class ConfigurationCacheSkipCacheIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "skip reading cached state on #commandLine"() {

        def configurationCache = newConfigurationCacheFixture()

        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {

                @Input abstract Property<String> getMessage()

                @TaskAction def action() {
                    println(message.get())
                }
            }
            tasks.register("myTask", MyTask) {
                // use an undeclared input so we can test --refresh-dependencies
                // URL.text is not tracked for now; we'll have to change it once we start to track it
                message.set(file("message").toPath().toUri().toURL().text)
            }
        """
        file("message") << "foo"

        when:
        configurationCacheRun "myTask"

        then:
        outputContains("foo")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "myTask"

        then:
        outputContains("foo")
        configurationCache.assertStateLoaded()

        when:
        file("message").text = "bar"

        and:
        configurationCacheRun "myTask"

        then:
        outputContains("foo")
        configurationCache.assertStateLoaded()

        when:
        def commandLineArgs = commandLine.split("\\s+")
        executer.withArguments(commandLineArgs)
        configurationCacheRun "myTask"

        then:
        outputContains("bar")
        outputContains("Calculating task graph as configuration cache cannot be reused due to ${commandLineArgs.first()}")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "myTask"

        then:
        outputContains("bar")
        configurationCache.assertStateLoaded()

        where:
        commandLine              | _
        "--refresh-dependencies" | _
        "--write-locks"          | _
        "--update-locks thing:*" | _
    }
}
