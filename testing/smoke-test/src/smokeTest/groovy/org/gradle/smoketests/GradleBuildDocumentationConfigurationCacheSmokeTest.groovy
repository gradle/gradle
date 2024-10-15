/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.smoketests

import groovy.test.NotYetImplemented
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Ignore

class GradleBuildDocumentationConfigurationCacheSmokeTest extends AbstractGradleBuildConfigurationCacheSmokeTest {

    def "can build documentation with configuration cache enabled"() {

        given:
        def tasks = [
            ':docs:dslHtml',
            ':docs:releaseNotes',
            ':docs:generateDocInfo',
            ':docs:apiMapping',
            ':docs:defaultImports',
            ':docs:checkDeadInternalLinks',
            ':docs:checkstyleApi',
            ':docs:incubationReport',
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        result.assertConfigurationCacheStateStored()

        when:
        run([":docs:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        result.assertConfigurationCacheStateLoaded()
        result.task(":docs:dslHtml").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:releaseNotes").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:generateDocInfo").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:apiMapping").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:defaultImports").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:checkDeadInternalLinks").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:checkstyleApi").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:incubationReport").outcome == TaskOutcome.FROM_CACHE
    }

    @Ignore("Broken by at least the Asciidoctor plugin, and takes 40mins on CI")
    @NotYetImplemented
    def "can build and test Gradle documentation with configuration cache enabled"() {

        given:
        def tasks = [
            ':docs:docs',
            ':docs:docsTest',
            "-D${StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}=8192".toString(), // TODO:configuration-cache remove
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        result.assertConfigurationCacheStateStored()

        when:
        run([":docs:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        result.assertConfigurationCacheStateLoaded()
        result.task(":docs:docs").outcome == TaskOutcome.SUCCESS
        result.task("':docs:docsTest'").outcome == TaskOutcome.SUCCESS
    }

    def "can resolve classpath for :docs:embeddedCrossVersionTest with configuration cache enabled"() {
        given:
        def tasks = [":docs:embeddedCrossVersionTest", "--dry-run"]

        when:
        configurationCacheRun(tasks)

        then:
        result.assertConfigurationCacheStateStored()
    }
}
