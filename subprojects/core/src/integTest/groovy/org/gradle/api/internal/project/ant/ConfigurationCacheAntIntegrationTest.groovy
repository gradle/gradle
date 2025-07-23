/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.project.ant

import groovy.xml.MarkupBuilder
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(value = IntegTestPreconditions.NotConfigCached, reason = "handles CC explicitly")
class ConfigurationCacheAntIntegrationTest extends AbstractIntegrationSpec {

    File antBuildFile
    def configurationCache = newConfigurationCacheFixture()

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
    }

    def setup() {
        antBuildFile = new File(testDirectory, 'build.xml')
        antBuildFile.withWriter { Writer writer ->
            def xml = new MarkupBuilder(writer)
            xml.project {
                target(name: 'test-build') {
                    echo(message: 'Basedir is: ${basedir}')
                }
            }
        }
    }

    def "can import Ant build with configuration cache enabled with graceful degradation"() {
        given:
        buildFile """
            ant.importBuild 'build.xml'
        """

        when:
        run('test-build')

        then:
        outputContains("[ant:echo] Basedir is: " + testDirectory.getAbsolutePath())
        configurationCache.assertNoConfigurationCache()
        postBuildOutputContains("Configuration cache disabled because incompatible task was found.")
    }

    def "manually registered AntTarget task works with configuration cache enabled with graceful degradation"() {
        given:
        buildFile """
            tasks.register("test-build", AntTarget) {
                target = ant.target(name: 'test-build') {
                    echo(message: 'Hello from Ant!')
                }
                baseDir = project.layout.projectDirectory.asFile
            }
        """

        when:
        run('test-build')

        then:
        outputContains("[ant:echo] Hello from Ant!")
        configurationCache.assertNoConfigurationCache()
        postBuildOutputContains("Configuration cache disabled because incompatible task was found.")
    }
}
