/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.plugins

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.InvalidPluginException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class RuleSourceApplicationTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private ProjectInternal buildProject() {
        ProjectBuilder.builder().withProjectDir(temporaryFolder.testDirectory).build()
    }

    def "can apply a rule source by id"() {
        when:
        def project = buildProject()
        project.apply plugin: 'custom-rule-source'

        then:
        project.modelRegistry.realize("foo", String) == "bar"
    }

    def "can apply a rule source by type"() {
        when:
        def project = buildProject()
        project.apply type: CustomRuleSource

        then:
        project.modelRegistry.realize("foo", String) == "bar"
    }

    def "cannot apply a type that is neither a plugin nor a rule source"() {
        when:
        def project = buildProject()
        project.apply type: String

        then:
        PluginApplicationException e = thrown()
        e.cause instanceof InvalidPluginException
        e.cause.message == "'${String.name}' is neither a plugin or a rule source and cannot be applied."
    }

    def "cannot apply a rule source to a non model rule scope element"() {
        when:
        def project = buildProject()
        project.gradle.apply plugin: "custom-rule-source"

        then:
        PluginApplicationException e = thrown()
        e.cause instanceof UnsupportedOperationException
        e.cause.message == "Cannot apply model rules of plugin '${CustomRuleSource.name}' as the target 'build 'test'' is not model rule aware"
    }

    def "useful message is presented when applying rule source only type as a plugin"() {
        when:
        def project = buildProject()
        project.apply plugin: CustomRuleSource

        then:
        project.modelRegistry.realize("foo", String) == "bar"
    }

    def "can use id to check for applied plugins and rule sources"() {
        when:
        def project = buildProject()

        then:
        !project.pluginManager.hasPlugin("custom-plugin")
        !project.pluginManager.hasPlugin("custom-rule-source")

        when:
        project.apply plugin: "custom-plugin"
        project.apply plugin: "custom-rule-source"

        then:
        project.pluginManager.hasPlugin("custom-plugin")
        project.pluginManager.hasPlugin("custom-rule-source")
    }
}
