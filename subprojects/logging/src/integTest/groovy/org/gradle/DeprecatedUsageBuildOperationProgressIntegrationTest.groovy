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
            org.gradle.internal.deprecation.DeprecationLogger.deprecate('Init script').willBeRemovedInGradle9().undocumented().nagUser();
            org.gradle.internal.deprecation.DeprecationLogger.deprecate('Init script').willBeRemovedInGradle9().undocumented().nagUser();
        """

        file('script.gradle') << """
            org.gradle.internal.deprecation.DeprecationLogger.deprecate('Plugin script').willBeRemovedInGradle9().undocumented().nagUser();
        """

        buildScript """
            apply from: 'script.gradle'
            apply plugin: SomePlugin

            org.gradle.internal.deprecation.DeprecationLogger.deprecateBuildInvocationFeature('Some invocation feature').withAdvice("Don't do custom invocation.").willBeRemovedInGradle9().undocumented().nagUser()
            org.gradle.internal.deprecation.DeprecationLogger.deprecateIndirectUsage('Some indirect deprecation').withAdvice('Some advice.').willBeRemovedInGradle9().undocumented().nagUser()

            task t(type:SomeTask) {
                doLast {
                    org.gradle.internal.deprecation.DeprecationLogger.deprecate('Custom Task action')
                            .withAdvice('Use task type X instead.').withContext("Task ':t' should not have custom actions attached.")
                            .willBeRemovedInGradle9()
                            .undocumented()
                            .nagUser()
                }
            }

            task t2(type:SomeTask)

            class SomePlugin implements Plugin<Project> {
                void apply(Project p){
                    org.gradle.internal.deprecation.DeprecationLogger.deprecate('Plugin').willBeRemovedInGradle9().undocumented().nagUser();
                }
            }

            class SomeTask extends DefaultTask {
                @TaskAction
                void someAction(){
                    org.gradle.internal.deprecation.DeprecationLogger.deprecate('Typed task').willBeRemovedInGradle9().undocumented().nagUser();
                }
            }

        """
        and:
        executer.noDeprecationChecks()
        succeeds 't', 't2', '-I', 'init.gradle'

        then:
        def initDeprecation = operations.only("Apply initialization script 'init.gradle' to build").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        initDeprecation.details.summary == 'Init script has been deprecated.'
        initDeprecation.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        initDeprecation.details.advice == null
        initDeprecation.details.contextualAdvice == null
        initDeprecation.details.stackTrace.size > 0
        initDeprecation.details.stackTrace[0].fileName.endsWith('init.gradle')
        initDeprecation.details.stackTrace[0].lineNumber == 2

        def pluginDeprecation = operations.only("Apply plugin SomePlugin to root project 'root'").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        pluginDeprecation.details.summary == 'Plugin has been deprecated.'
        pluginDeprecation.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        pluginDeprecation.details.advice == null
        pluginDeprecation.details.contextualAdvice == null
        pluginDeprecation.details.type == 'USER_CODE_DIRECT'
        pluginDeprecation.details.stackTrace.size > 0
        pluginDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        pluginDeprecation.details.stackTrace[0].lineNumber == 22

        def invocationDeprecation = operations.only("Apply build file 'build.gradle' to root project 'root'").progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }[0]
        invocationDeprecation.details.summary == 'Some invocation feature has been deprecated.'
        invocationDeprecation.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        invocationDeprecation.details.advice == "Don't do custom invocation."
        invocationDeprecation.details.type == "BUILD_INVOCATION"
        invocationDeprecation.details.stackTrace.size > 0
        invocationDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        invocationDeprecation.details.stackTrace[0].lineNumber == 5

        def userIndirectCodeDeprecation = operations.only("Apply build file 'build.gradle' to root project 'root'").progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }[1]
        userIndirectCodeDeprecation.details.summary == 'Some indirect deprecation has been deprecated.'
        userIndirectCodeDeprecation.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        userIndirectCodeDeprecation.details.advice == "Some advice."
        userIndirectCodeDeprecation.details.type == 'USER_CODE_INDIRECT'
        userIndirectCodeDeprecation.details.stackTrace.size > 0
        userIndirectCodeDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        userIndirectCodeDeprecation.details.stackTrace[0].lineNumber == 6

        def scriptPluginDeprecation = operations.only("Apply script 'script.gradle' to root project 'root'").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        scriptPluginDeprecation.details.summary == 'Plugin script has been deprecated.'
        scriptPluginDeprecation.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        scriptPluginDeprecation.details.advice == null
        scriptPluginDeprecation.details.type == 'USER_CODE_DIRECT'
        scriptPluginDeprecation.details.stackTrace.size > 0
        scriptPluginDeprecation.details.stackTrace[0].fileName.endsWith('script.gradle')
        scriptPluginDeprecation.details.stackTrace[0].lineNumber == 2

        def taskDoLastDeprecation = operations.only("Execute doLast {} action for :t").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        taskDoLastDeprecation.details.summary == 'Custom Task action has been deprecated.'
        taskDoLastDeprecation.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        taskDoLastDeprecation.details.advice == 'Use task type X instead.'
        taskDoLastDeprecation.details.contextualAdvice == "Task ':t' should not have custom actions attached."
        taskDoLastDeprecation.details.type == 'USER_CODE_DIRECT'
        taskDoLastDeprecation.details.stackTrace.size > 0
        taskDoLastDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        taskDoLastDeprecation.details.stackTrace[0].lineNumber == 10

        def typedTaskDeprecation = operations.only("Execute someAction for :t").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        typedTaskDeprecation.details.summary == 'Typed task has been deprecated.'
        typedTaskDeprecation.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        typedTaskDeprecation.details.advice == null
        typedTaskDeprecation.details.contextualAdvice == null
        typedTaskDeprecation.details.type == 'USER_CODE_DIRECT'
        typedTaskDeprecation.details.stackTrace.size > 0
        typedTaskDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation.details.stackTrace[0].lineNumber == 29
        typedTaskDeprecation.details.stackTrace[0].methodName == 'someAction'

        def typedTaskDeprecation2 = operations.only("Execute someAction for :t2").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        typedTaskDeprecation2.details.summary == 'Typed task has been deprecated.'
        typedTaskDeprecation2.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        typedTaskDeprecation2.details.advice == null
        typedTaskDeprecation2.details.contextualAdvice == null
        typedTaskDeprecation2.details.type == 'USER_CODE_DIRECT'
        typedTaskDeprecation2.details.stackTrace.size > 0
        typedTaskDeprecation2.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation2.details.stackTrace[0].lineNumber == 29
        typedTaskDeprecation2.details.stackTrace[0].methodName == 'someAction'
    }

    def "emits deprecation warnings as build operation progress events for buildSrc builds"() {
        when:
        file('buildSrc/build.gradle') << """
            org.gradle.internal.deprecation.DeprecationLogger.deprecate('BuildSrc script').willBeRemovedInGradle9().undocumented().nagUser();
        """

        and:
        executer.noDeprecationChecks()
        succeeds 'help'

        then:
        def buildSrcDeprecations = operations.only("Apply build file 'buildSrc${File.separator}build.gradle' to project ':buildSrc'").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        buildSrcDeprecations.details.summary.contains('BuildSrc script has been deprecated.')
        buildSrcDeprecations.details.removalDetails.contains('This is scheduled to be removed in Gradle 9.0.')
        buildSrcDeprecations.details.advice == null
        buildSrcDeprecations.details.contextualAdvice == null
        buildSrcDeprecations.details.type == 'USER_CODE_DIRECT'
        buildSrcDeprecations.details.stackTrace.size > 0
        buildSrcDeprecations.details.stackTrace[0].fileName.endsWith("buildSrc${File.separator}build.gradle")
        buildSrcDeprecations.details.stackTrace[0].lineNumber == 2
    }

    def "emits deprecation warnings as build operation progress events for composite builds"() {
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << """
            org.gradle.internal.deprecation.DeprecationLogger.deprecate('Included build script').willBeRemovedInGradle9().undocumented().nagUser();

            task t {
                doLast {
                    org.gradle.internal.deprecation.DeprecationLogger.deprecate('Included build task').willBeRemovedInGradle9().undocumented().nagUser();
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
        def includedBuildScriptDeprecations = operations.only("Apply build file 'included${File.separator}build.gradle' to project ':included'").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        includedBuildScriptDeprecations.details.summary == 'Included build script has been deprecated.'
        includedBuildScriptDeprecations.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        includedBuildScriptDeprecations.details.advice == null
        includedBuildScriptDeprecations.details.contextualAdvice == null
        includedBuildScriptDeprecations.details.type == 'USER_CODE_DIRECT'
        includedBuildScriptDeprecations.details.stackTrace.size > 0
        includedBuildScriptDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildScriptDeprecations.details.stackTrace[0].lineNumber == 2

        def includedBuildTaskDeprecations = operations.only("Execute doLast {} action for :included:t").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        includedBuildTaskDeprecations.details.summary == 'Included build task has been deprecated.'
        includedBuildTaskDeprecations.details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        includedBuildTaskDeprecations.details.advice == null
        includedBuildTaskDeprecations.details.contextualAdvice == null
        includedBuildTaskDeprecations.details.type == 'USER_CODE_DIRECT'
        includedBuildTaskDeprecations.details.stackTrace.size > 0
        includedBuildTaskDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildTaskDeprecations.details.stackTrace[0].lineNumber == 6
    }

    def "collects stack traces for deprecation usages at certain limit, regardless of whether the deprecation has been encountered before"() {
        file('settings.gradle') << "rootProject.name = 'root'"

        51.times {
            buildFile << """
                org.gradle.internal.deprecation.DeprecationLogger.deprecate('Thing $it').willBeRemovedInGradle9().undocumented().nagUser();
            """
        }

        when:
        executer.noDeprecationChecks()
        run()

        then:
        def events = operations.only("Apply build file 'build.gradle' to root project 'root'").progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        events.size() == 51
        events[0].details.stackTrace.size > 0
        events[50].details.stackTrace.size == 0
    }
}
