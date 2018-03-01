/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.logging

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.junit.Rule

import java.util.regex.Pattern

class LoggingBuildOperationNotificationIntegTest extends AbstractIntegrationSpec {

    @Rule public final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)
    MavenHttpRepository mavenHttpRepository = new MavenHttpRepository(server, '/repo', mavenRepo)

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "captures output sources with context"() {
        given:
        mavenHttpRepository.module("org", "foo", '1.0').publish().allowAll()

        file('init.gradle') << """
            logger.warn 'from init.gradle'
        """
        settingsFile << """
            rootProject.name = 'root'
            println 'from settings file'
        """

        file("build.gradle") << """
            apply plugin: 'java'
    
            repositories {
                maven { url "${mavenHttpRepository.uri}" }
            }
            
            dependencies {
                runtime 'org:foo:1.0'
            }
            
            jar.doLast {
                println 'from jar task'
            }
        
            task resolve {
                doLast {
                    // force resolve
                    configurations.runtime.files
                }
            }
            
            build.dependsOn resolve
                        
            logger.lifecycle('from build.gradle')
        
            gradle.taskGraph.whenReady{
                logger.warn('warning from taskgraph')
            }
        """

        when:
        succeeds("build", '-I', 'init.gradle')

        then:

        def applyInitScriptProgress = operations.only('Apply script init.gradle to build').progress
        applyInitScriptProgress.size() == 1
        applyInitScriptProgress[0].details.logLevel == 'WARN'
        applyInitScriptProgress[0].details.category == 'org.gradle.api.Script'
        applyInitScriptProgress[0].details.message == 'from init.gradle'

        def applySettingsScriptProgress = operations.only(Pattern.compile('Apply script settings.gradle .*')).progress
        applySettingsScriptProgress.size() == 1
        applySettingsScriptProgress[0].details.logLevel == 'QUIET'
        applySettingsScriptProgress[0].details.category == 'system.out'
        applySettingsScriptProgress[0].details.spanDetails[0].style == 'Normal'
        applySettingsScriptProgress[0].details.spanDetails[0].text == 'from settings file\n'

        def applyBuildScriptProgress = operations.only("Apply script build.gradle to root project 'root'").progress
        applyBuildScriptProgress.size() == 1
        applyBuildScriptProgress[0].details.logLevel == 'LIFECYCLE'
        applyBuildScriptProgress[0].details.category == 'org.gradle.api.Project'
        applyBuildScriptProgress[0].details.message == 'from build.gradle'

        def runTasksProgress = operations.only("Run tasks").progress
        runTasksProgress.size() == 1
        runTasksProgress[0].details.logLevel == 'WARN'
        runTasksProgress[0].details.category == 'org.gradle.api.Project'
        runTasksProgress[0].details.message == 'warning from taskgraph'

        def jarProgress = operations.only("Execute doLast {} action for :jar").progress
        jarProgress.size() == 1
        jarProgress[0].details.logLevel == 'QUIET'
        jarProgress[0].details.category == 'system.out'
        jarProgress[0].details.spanDetails.size == 1
        jarProgress[0].details.spanDetails[0].style == 'Normal'
        jarProgress[0].details.spanDetails[0].text == 'from jar task\n'

        def downloadProgress = operations.only("Download http://localhost:${server.port}/repo/org/foo/1.0/foo-1.0.jar").progress
        downloadProgress.size() == 1
        downloadProgress[0].details.logLevel == 'LIFECYCLE'
        downloadProgress[0].details.category == 'org.gradle.internal.progress.DefaultBuildOperationExecutor'
        downloadProgress[0].details.description == "Download http://localhost:${server.port}/repo/org/foo/1.0/foo-1.0.jar"
    }

    @spock.lang.Ignore
    def "captures output from nested builds"() {
        given:
        file('nested/settings.gradle') << "rootProject.name = 'nested'"
        file('nested/build.gradle') << "task foo{doLast {println 'foo'}}"

        settingsFile << "rootProject.name = 'root'"
        file("build.gradle") << """
        task run(type:GradleBuild) {
            dir = 'nested'
            tasks = ['foo']
        }
        """

        when:
        succeeds "run"

        then:
        // should be exec :nested:foo build op that contains the foo output progress
        def runFooProgress = operations.only("Run tasks (:nested)").progress
        runFooProgress.size() == 1
        runFooProgress[0].details.logLevel == 'LIFECYCLE'
        runFooProgress[0].details.category == 'org.gradle.api.Project'
        runFooProgress[0].details.message == 'output from build.gradle'
    }

}
