/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r212

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.eclipse.EclipseProject
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification

@Ignore
class ToolingApiCompositeSpec extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()

    def setup() {
        temporaryFolder.file("build.gradle") << """
            allprojects {
                apply plugin: 'java'
                group = 'group'
                version = '1.0'
            }
"""
       temporaryFolder.file("settings.gradle") << """
            include 'a', 'b', 'c'
"""
    }

    def "can create composite"() {
        given:
        def connection = GradleConnector.newGradleConnectionBuilder().
            addBuild(temporaryFolder.testDirectory, dist.binDistribution.toURI()).
            build()
        expect:
        def models = connection.getModels(EclipseProject)
        models.size() == 4
    }
}
