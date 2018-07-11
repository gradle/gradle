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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.featurelifecycle.DeprecatedUsageProgressDetails

class DeprecationMessageBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits deprecation warnings as build operation progress events with context"() {
        when:
        file('settings.gradle') << "rootProject.name = 'root'"

        file('init.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('init script deprecated');
        """

        file('script.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('plugin script deprecated');
        """

        buildScript """
            apply from: 'script.gradle'
            apply plugin: SomePlugin
            
            task t(type:SomeTask) {
                doLast {
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Task t deprecated');
                }
            }
            
            task t2(type:SomeTask)
            
            class SomePlugin implements Plugin<Project> {
                void apply(Project p){
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('plugin deprecated');
                }
            }
            
            class SomeTask extends DefaultTask {
                @TaskAction
                void someAction(){
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('typed task deprecated');
                }
            }
        """
        and:
        executer.noDeprecationChecks()
        succeeds 't','t2', '-I', 'init.gradle'

        then:
        def initDeprecation = operations.only("Apply script init.gradle to build").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        initDeprecation.details.message.contains('init script deprecated')
        initDeprecation.details.stackTrace.size > 0
        initDeprecation.details.stackTrace[0].fileName.endsWith('init.gradle')
        initDeprecation.details.stackTrace[0].lineNumber == 2

        def pluginDeprecation = operations.only("Apply plugin SomePlugin to root project 'root'").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        pluginDeprecation.details.message.contains('plugin deprecated')
        pluginDeprecation.details.stackTrace.size > 0
        pluginDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        pluginDeprecation.details.stackTrace[0].lineNumber == 15

        def scriptPluginDeprecation = operations.only("Apply script script.gradle to root project 'root'").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        scriptPluginDeprecation.details.message.contains('plugin script deprecated')
        scriptPluginDeprecation.details.stackTrace.size > 0
        scriptPluginDeprecation.details.stackTrace[0].fileName.endsWith('script.gradle')
        scriptPluginDeprecation.details.stackTrace[0].lineNumber == 2

        def taskDoLastDeprecation = operations.only("Execute doLast {} action for :t").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        taskDoLastDeprecation.details.message.contains('Task t deprecated')
        taskDoLastDeprecation.details.stackTrace.size > 0
        taskDoLastDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        taskDoLastDeprecation.details.stackTrace[0].lineNumber == 7

        def typedTaskDeprecation = operations.only("Execute someAction for :t").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        typedTaskDeprecation.details.message.contains('typed task deprecated')
        typedTaskDeprecation.details.stackTrace.size > 0
        typedTaskDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation.details.stackTrace[0].lineNumber == 22
        typedTaskDeprecation.details.stackTrace[0].methodName == 'someAction'

        def typedTaskDeprecation2 = operations.only("Execute someAction for :t2").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        typedTaskDeprecation2.details.message.contains('typed task deprecated')
        typedTaskDeprecation2.details.stackTrace.size > 0
        typedTaskDeprecation2.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation2.details.stackTrace[0].lineNumber == 22
        typedTaskDeprecation2.details.stackTrace[0].methodName == 'someAction'
    }

    def "emits deprecation warnings as build operation progress events for buildSrc builds"() {
        when:
        file('buildSrc/build.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('buildSrc script deprecated');
        """

        and:
        executer.noDeprecationChecks()
        succeeds 'help'

        then:
        def buildSrcDeprecations = operations.only("Apply script build.gradle to project ':buildSrc'").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        buildSrcDeprecations.details.message.contains('buildSrc script deprecated')
        buildSrcDeprecations.details.stackTrace.size > 0
        buildSrcDeprecations.details.stackTrace[0].fileName.endsWith("buildSrc${File.separator}build.gradle")
        buildSrcDeprecations.details.stackTrace[0].lineNumber == 2
    }

    def "emits deprecation warnings as build operation progress events for composite builds"() {
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('included build script deprecated');
            
            task t {
                doLast {
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('included build task deprecated');
                }
            }
        """
        file('settings.gradle') << """
        rootProject.name = 'root'
        includeBuild('included')
        """

        when:
        buildFile << """
            task t { dependsOn gradle.includedBuilds*.task(':t') } 
        """

        and:
        executer.noDeprecationChecks()
        succeeds 't'

        then:
        def includedBuildScriptDeprecations = operations.only("Apply script build.gradle to project ':included'").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        includedBuildScriptDeprecations.details.message.contains('included build script deprecated')
        includedBuildScriptDeprecations.details.stackTrace.size > 0
        includedBuildScriptDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildScriptDeprecations.details.stackTrace[0].lineNumber == 2

        def includedBuildTaskDeprecations = operations.only("Execute doLast {} action for :included:t").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        includedBuildTaskDeprecations.details.message.contains('included build task deprecated')
        includedBuildTaskDeprecations.details.stackTrace.size > 0
        includedBuildTaskDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildTaskDeprecations.details.stackTrace[0].lineNumber == 6
    }
}
