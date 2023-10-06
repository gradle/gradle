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

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.featurelifecycle.DeprecatedUsageProgressDetails

class DeprecatedUsageBuildOperationProgressIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits deprecation warnings as build operation progress events with context"() {
        when:
        settingsFile "rootProject.name = 'root'"

        initScript  """
            org.gradle.internal.deprecation.DeprecationLogger.deprecate('Init script')
                .willBeRemovedInGradle9()
                .undocumented()
                .nagUser()
            org.gradle.internal.deprecation.DeprecationLogger.deprecate('Init script')
                  .willBeRemovedInGradle9()
                  .undocumented()
                  .nagUser()
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
        def initDeprecation = operations.only("Apply initialization script 'init.gradle' to build").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }.each {}
        Map<String, Object> initDeprecationDetails = initDeprecation.details['deprecation'] as Map<String, Object>
        initDeprecationDetails.summary == 'Init script has been deprecated.'
        initDeprecationDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        initDeprecationDetails.advice == null
        initDeprecationDetails.contextualAdvice == null
        initDeprecationDetails.stackTrace.length() > 0
        initDeprecationDetails.stackTrace.contains('init.gradle:2')

        def pluginDeprecation = operations.only("Apply plugin SomePlugin to root project 'root'").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        Map<String, Object> pluginDeprecationDetails = pluginDeprecation.details['deprecation'] as Map<String, Object>
        pluginDeprecationDetails.summary == 'Plugin has been deprecated.'
        pluginDeprecationDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        pluginDeprecationDetails.advice == null
        pluginDeprecationDetails.contextualAdvice == null
        pluginDeprecationDetails.type == 'USER_CODE_DIRECT'
        pluginDeprecationDetails.stackTrace.length() > 0
        pluginDeprecationDetails.stackTrace.contains('build.gradle:22')

        def invocationDeprecation = operations.only("Apply build file 'build.gradle' to root project 'root'").progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }[0]
        Map<String, Object> invocationDeprecationDetails = invocationDeprecation.details['deprecation'] as Map<String, Object>
        invocationDeprecationDetails.summary == 'Some invocation feature has been deprecated.'
        invocationDeprecationDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        invocationDeprecationDetails.advice == "Don't do custom invocation."
        invocationDeprecationDetails.type == "BUILD_INVOCATION"
        invocationDeprecationDetails.stackTrace.length() > 0
        invocationDeprecationDetails.stackTrace.contains('build.gradle:5')

        def userIndirectCodeDeprecation = operations.only("Apply build file 'build.gradle' to root project 'root'").progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }[1]
        Map<String, Object> userIndirectCodeDeprecationDetails = userIndirectCodeDeprecation.details['deprecation'] as Map<String, Object>
        userIndirectCodeDeprecationDetails.summary == 'Some indirect deprecation has been deprecated.'
        userIndirectCodeDeprecationDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        userIndirectCodeDeprecationDetails.advice == "Some advice."
        userIndirectCodeDeprecationDetails.type == 'USER_CODE_INDIRECT'
        userIndirectCodeDeprecationDetails.stackTrace.length() > 0
        userIndirectCodeDeprecationDetails.stackTrace.contains('build.gradle:6')

        def scriptPluginDeprecation = operations.only("Apply script 'script.gradle' to root project 'root'").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        Map<String, Object> scriptPluginDeprecationDetails = scriptPluginDeprecation.details['deprecation'] as Map<String, Object>
        scriptPluginDeprecationDetails.summary == 'Plugin script has been deprecated.'
        scriptPluginDeprecationDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        scriptPluginDeprecationDetails.advice == null
        scriptPluginDeprecationDetails.type == 'USER_CODE_DIRECT'
        scriptPluginDeprecationDetails.stackTrace.length() > 0
        scriptPluginDeprecationDetails.stackTrace.contains('script.gradle:2')

        def taskDoLastDeprecation = operations.only("Execute doLast {} action for :t").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        Map<String, Object> taskDoLastDeprecationDetails = taskDoLastDeprecation.details['deprecation'] as Map<String, Object>
        taskDoLastDeprecationDetails.summary == 'Custom Task action has been deprecated.'
        taskDoLastDeprecationDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        taskDoLastDeprecationDetails.advice == 'Use task type X instead.'
        taskDoLastDeprecationDetails.contextualAdvice == "Task ':t' should not have custom actions attached."
        taskDoLastDeprecationDetails.type == 'USER_CODE_DIRECT'
        taskDoLastDeprecationDetails.stackTrace.length() > 0
        taskDoLastDeprecationDetails.stackTrace.contains('build.gradle:10')

        def typedTaskDeprecation = operations.only("Execute someAction for :t").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        Map<String, Object> typedTaskDeprecationDetails = typedTaskDeprecation.details['deprecation'] as Map<String, Object>
        typedTaskDeprecationDetails.summary == 'Typed task has been deprecated.'
        typedTaskDeprecationDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        typedTaskDeprecationDetails.advice == null
        typedTaskDeprecationDetails.contextualAdvice == null
        typedTaskDeprecationDetails.type == 'USER_CODE_DIRECT'
        typedTaskDeprecationDetails.stackTrace.length() > 0
        typedTaskDeprecationDetails.stackTrace.contains('build.gradle:29')
        typedTaskDeprecationDetails.stackTrace.contains('someAction')

        def typedTaskDeprecation2 = operations.only("Execute someAction for :t2").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        Map<String, Object> typedTaskDeprecation2Details = typedTaskDeprecation2.details['deprecation'] as Map<String, Object>
        typedTaskDeprecation2Details.summary == 'Typed task has been deprecated.'
        typedTaskDeprecation2Details.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        typedTaskDeprecation2Details.advice == null
        typedTaskDeprecation2Details.contextualAdvice == null
        typedTaskDeprecation2Details.type == 'USER_CODE_DIRECT'
        typedTaskDeprecation2Details.stackTrace.length() > 0
        typedTaskDeprecation2Details.stackTrace.contains('build.gradle:29')
        typedTaskDeprecation2Details.stackTrace.contains('someAction')
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
        Map<String, Object> buildSrcDeprecationsDetails = buildSrcDeprecations.details['deprecation'] as Map<String, Object>
        buildSrcDeprecationsDetails.summary.contains('BuildSrc script has been deprecated.')
        buildSrcDeprecationsDetails.removalDetails.contains('This is scheduled to be removed in Gradle 9.0.')
        buildSrcDeprecationsDetails.advice == null
        buildSrcDeprecationsDetails.contextualAdvice == null
        buildSrcDeprecationsDetails.type == 'USER_CODE_DIRECT'
        buildSrcDeprecationsDetails.stackTrace.length() > 0
        buildSrcDeprecationsDetails.stackTrace.contains("buildSrc${File.separator}build.gradle:2")
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
        Map<String, Object> includedBuildScriptDeprecationsDetails = includedBuildScriptDeprecations.details['deprecation'] as Map<String, Object>
        includedBuildScriptDeprecationsDetails.summary == 'Included build script has been deprecated.'
        includedBuildScriptDeprecationsDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        includedBuildScriptDeprecationsDetails.advice == null
        includedBuildScriptDeprecationsDetails.contextualAdvice == null
        includedBuildScriptDeprecationsDetails.type == 'USER_CODE_DIRECT'
        includedBuildScriptDeprecationsDetails.stackTrace.length() > 0
        includedBuildScriptDeprecationsDetails.stackTrace.contains("included${File.separator}build.gradle:2")

        def includedBuildTaskDeprecations = operations.only("Execute doLast {} action for :included:t").progress.find { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        Map<String, Object> includedBuildTaskDeprecationsDetails = includedBuildTaskDeprecations.details['deprecation'] as Map<String, Object>
        includedBuildTaskDeprecationsDetails.summary == 'Included build task has been deprecated.'
        includedBuildTaskDeprecationsDetails.removalDetails == 'This is scheduled to be removed in Gradle 9.0.'
        includedBuildTaskDeprecationsDetails.advice == null
        includedBuildTaskDeprecationsDetails.contextualAdvice == null
        includedBuildTaskDeprecationsDetails.type == 'USER_CODE_DIRECT'
        includedBuildTaskDeprecationsDetails.stackTrace.length() > 0
        includedBuildTaskDeprecationsDetails.stackTrace.contains("included${File.separator}build.gradle:6")
    }

    def "collects stack traces for deprecation usages at certain limit, regardless of whether the deprecation has been encountered before for warning mode #mode"() {
        file('settings.gradle') << "rootProject.name = 'root'"

        51.times {
            buildFile << """
                org.gradle.internal.deprecation.DeprecationLogger.deprecate('Thing $it').willBeRemovedInGradle9().undocumented().nagUser();
            """
        }

        when:
        executer.noDeprecationChecks().withWarningMode(mode)
        run()

        then:
        def events = operations.only("Apply build file 'build.gradle' to root project 'root'").progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        events.size() == 51
        events[0].details['deprecation'].stackTrace.length() > 0
        events[50].details['deprecation'].stackTrace.length() == 0
        where:
        mode << [WarningMode.None, WarningMode.Summary]
    }

    def "collects stack traces for deprecation usages without limit for warning mode #mode"() {
        file('settings.gradle') << "rootProject.name = 'root'"

        100.times {
            buildFile << """
                org.gradle.internal.deprecation.DeprecationLogger.deprecate('Thing $it').willBeRemovedInGradle9().undocumented().nagUser();
            """
        }

        when:
        executer.noDeprecationChecks().withWarningMode(mode)
        if (mode == WarningMode.Fail) {
            runAndFail()
        } else {
            run()
        }

        then:
        def events = operations.only("Apply build file 'build.gradle' to root project 'root'").progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        events.size() == 100
        events.every { it.details['deprecation'].stackTrace.length() > 0 }

        where:
        mode << [WarningMode.All, WarningMode.Fail]
    }
}
