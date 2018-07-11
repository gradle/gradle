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

class DeprecatedUsageBuildOperationProgressIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits deprecation warnings as build operation progress events with context"() {
        when:
        file('settings.gradle') << "rootProject.name = 'root'"

        file('init.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Init script');
        """

        file('script.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Plugin script');
        """

        buildScript """
            apply from: 'script.gradle'
            apply plugin: SomePlugin
            
            task t(type:SomeTask) {
                doLast {
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Task t', 'Use task t2 instead.');
                }
            }
            
            task t2(type:SomeTask)
            
            class SomePlugin implements Plugin<Project> {
                void apply(Project p){
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Plugin');
                }
            }
            
            class SomeTask extends DefaultTask {
                @TaskAction
                void someAction(){
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Typed task');
                }
            }
        """
        and:
        executer.noDeprecationChecks()
        succeeds 't','t2', '-I', 'init.gradle'

        then:
        def initDeprecation = operations.only("Apply script init.gradle to build").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        initDeprecation.details.message == 'Init script has been deprecated.'
        initDeprecation.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        initDeprecation.details.advice == null
        initDeprecation.details.stackTrace.size > 0
        initDeprecation.details.stackTrace[0].fileName.endsWith('init.gradle')
        initDeprecation.details.stackTrace[0].lineNumber == 2

        def pluginDeprecation = operations.only("Apply plugin SomePlugin to root project 'root'").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        pluginDeprecation.details.message == 'Plugin has been deprecated.'
        pluginDeprecation.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        pluginDeprecation.details.advice == null
        pluginDeprecation.details.stackTrace.size > 0
        pluginDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        pluginDeprecation.details.stackTrace[0].lineNumber == 15

        def scriptPluginDeprecation = operations.only("Apply script script.gradle to root project 'root'").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        scriptPluginDeprecation.details.message == 'Plugin script has been deprecated.'
        scriptPluginDeprecation.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        scriptPluginDeprecation.details.advice == null
        scriptPluginDeprecation.details.stackTrace.size > 0
        scriptPluginDeprecation.details.stackTrace[0].fileName.endsWith('script.gradle')
        scriptPluginDeprecation.details.stackTrace[0].lineNumber == 2

        def taskDoLastDeprecation = operations.only("Execute doLast {} action for :t").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        taskDoLastDeprecation.details.message == 'Task t has been deprecated.'
        taskDoLastDeprecation.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        taskDoLastDeprecation.details.advice == 'Use task t2 instead.'
        taskDoLastDeprecation.details.stackTrace.size > 0
        taskDoLastDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        taskDoLastDeprecation.details.stackTrace[0].lineNumber == 7

        def typedTaskDeprecation = operations.only("Execute someAction for :t").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        typedTaskDeprecation.details.message == 'Typed task has been deprecated.'
        typedTaskDeprecation.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        typedTaskDeprecation.details.advice == null
        typedTaskDeprecation.details.stackTrace.size > 0
        typedTaskDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation.details.stackTrace[0].lineNumber == 22
        typedTaskDeprecation.details.stackTrace[0].methodName == 'someAction'

        def typedTaskDeprecation2 = operations.only("Execute someAction for :t2").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        typedTaskDeprecation2.details.message == 'Typed task has been deprecated.'
        typedTaskDeprecation2.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        typedTaskDeprecation2.details.advice == null
        typedTaskDeprecation2.details.stackTrace.size > 0
        typedTaskDeprecation2.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation2.details.stackTrace[0].lineNumber == 22
        typedTaskDeprecation2.details.stackTrace[0].methodName == 'someAction'
    }

    def "emits deprecation warnings as build operation progress events for buildSrc builds"() {
        when:
        file('buildSrc/build.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('BuildSrc script');
        """

        and:
        executer.noDeprecationChecks()
        succeeds 'help'

        then:
        def buildSrcDeprecations = operations.only("Apply script build.gradle to project ':buildSrc'").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        buildSrcDeprecations.details.message.contains('BuildSrc script has been deprecated.')
        buildSrcDeprecations.details.details.contains('This is scheduled to be removed in Gradle 5.0.')
        buildSrcDeprecations.details.advice == null
        buildSrcDeprecations.details.stackTrace.size > 0
        buildSrcDeprecations.details.stackTrace[0].fileName.endsWith("buildSrc${File.separator}build.gradle")
        buildSrcDeprecations.details.stackTrace[0].lineNumber == 2
    }

    def "emits deprecation warnings as build operation progress events for composite builds"() {
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Included build script');
            
            task t {
                doLast {
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('Included build task');
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
        includedBuildScriptDeprecations.details.message == 'Included build script has been deprecated.'
        includedBuildScriptDeprecations.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        includedBuildScriptDeprecations.details.advice == null
        includedBuildScriptDeprecations.details.stackTrace.size > 0
        includedBuildScriptDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildScriptDeprecations.details.stackTrace[0].lineNumber == 2

        def includedBuildTaskDeprecations = operations.only("Execute doLast {} action for :included:t").progress.find {it.hasDetailsOfType(DeprecatedUsageProgressDetails)}
        includedBuildTaskDeprecations.details.message == 'Included build task has been deprecated.'
        includedBuildTaskDeprecations.details.details == 'This is scheduled to be removed in Gradle 5.0.'
        includedBuildTaskDeprecations.details.advice == null
        includedBuildTaskDeprecations.details.stackTrace.size > 0
        includedBuildTaskDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildTaskDeprecations.details.stackTrace[0].lineNumber == 6
    }
}
