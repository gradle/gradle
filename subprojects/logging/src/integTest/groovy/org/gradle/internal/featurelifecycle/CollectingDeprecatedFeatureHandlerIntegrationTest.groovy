/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.featurelifecycle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class CollectingDeprecatedFeatureHandlerIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def 'invocation at #phase from buildSrc should be displayed by default'() {
        given:
        buildSrcPluginWithWarningAtConfTime()
        buildSrcPluginWithWarningAtExecTime()

        file('buildSrc/build.gradle') << '''
apply plugin: 'groovy'
dependencies { compile gradleApi() }
'''
        buildFile << "apply plugin: ${pluginName}"

        when:
        executer.expectDeprecationWarning()
        succeeds(tasks as String[])

        then:
        output.contains("A deprecated API is used in buildSrc plugin at ${phase}")

        where:
        phase                | tasks                | pluginName
        'configuration time' | []                   | 'BuildSrcPluginWithWarningAtConfTime'
        'execution time'     | ['taskFromBuildSrc'] | 'BuildSrcPluginWithWarningAtExecTime'
    }

    def buildSrcPluginWithWarningAtConfTime() {
        file('buildSrc/src/main/groovy/BuildSrcPluginWithWarningAtConfTime.groovy') <<
            '''
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.SingleMessageLogger

class BuildSrcPluginWithWarningAtConfTime implements Plugin<Project> {
    @Override
    void apply(Project project) {
        SingleMessageLogger.nagUserWith("A deprecated API is used in buildSrc plugin at configuration time")
    }
}
'''
    }

    def buildSrcPluginWithWarningAtExecTime() {
        file('buildSrc/src/main/groovy/BuildSrcPluginWithWarningAtExecTime.groovy') <<
            '''
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.util.SingleMessageLogger
import org.gradle.api.tasks.TaskAction

class BuildSrcPluginWithWarningAtExecTime implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.create('taskFromBuildSrc', BuildSrcTask)
    }
}

class BuildSrcTask extends DefaultTask {
    @TaskAction
    void run() {
        SingleMessageLogger.nagUserWith("A deprecated API is used in buildSrc plugin at execution time")
    }
}
'''
    }

    @Unroll
    def 'invocation at #phase from script should be displayed by default'() {
        given:
        scriptWithWarningAtConfTime()
        scriptWithWarningAtExecTime()
        buildFile << "apply from: '${scriptName}.gradle'"

        when:
        executer.expectDeprecationWarning()
        succeeds(tasks as String[])

        then:
        output.contains("A deprecated API is used in script at ${phase}")

        where:
        phase                | tasks              | scriptName
        'configuration time' | []                 | 'scriptWithWarningAtConfTime'
        'execution time'     | ['taskFromScript'] | 'scriptWithWarningAtExecTime'
    }

    def scriptWithWarningAtConfTime() {
        file('scriptWithWarningAtConfTime.gradle') << '''
import org.gradle.util.SingleMessageLogger

SingleMessageLogger.nagUserWith('A deprecated API is used in script at configuration time')
'''
    }

    def scriptWithWarningAtExecTime() {
        file('scriptWithWarningAtExecTime.gradle') << '''
import org.gradle.api.DefaultTask
import org.gradle.util.SingleMessageLogger
import org.gradle.api.tasks.TaskAction

class TaskFromScript extends DefaultTask{
    @TaskAction
    void run() {
        SingleMessageLogger.nagUserWith("A deprecated API is used in script at execution time")
    }
}

project.tasks.create('taskFromScript', TaskFromScript)
'''
    }

    @Unroll
    def 'invocation at #phase from 3rd-party plugin should not be displayed by default'() {
        given:
        buildPluginJar()
        buildFile << """ 
buildscript {
    dependencies {
        classpath files('build/libs/MyPlugin.jar')
    }
}

apply plugin:'my.plugin.${pluginName}'
"""
        when:
        succeeds(tasks as String[])

        then:
        !output.contains("A deprecated API is used in 3rd-party plugin at ${phase}")

        where:
        phase                | tasks           | pluginName
        'configuration time' | []              | 'PluginWithWarningAtConfTime'
        'execution time'     | ['taskFromJar'] | 'PluginWithWarningAtExecTime'
    }

    def buildPluginJar() {
        file('src/main/groovy/PluginWithWarningAtConfTime.groovy') << '''
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.SingleMessageLogger

class PluginWithWarningAtConfTime implements Plugin<Project> {
    @Override
    void apply(Project project) {
        SingleMessageLogger.nagUserWith('A deprecated API is used in 3rd-party plugin at configuration time')
    }
}

'''
        file('src/main/groovy/PluginWithWarningAtExecTime.groovy') << '''
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.util.SingleMessageLogger
import org.gradle.api.tasks.TaskAction

class PluginWithWarningAtExecTime implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.create('taskFromJar', MyTask)
    }
}

class MyTask extends DefaultTask {
    @TaskAction
    void run() {
        SingleMessageLogger.nagUserWith('A deprecated API is used in 3rd-party plugin at execution time')
    }
}
'''
        buildFile << '''
apply plugin: 'groovy'

dependencies { compile gradleApi() }
'''
        settingsFile << "rootProject.name='MyPlugin'"

        file('src/main/resources/META-INF/gradle-plugins/my.plugin.PluginWithWarningAtConfTime.properties') << 'implementation-class=PluginWithWarningAtConfTime'
        file('src/main/resources/META-INF/gradle-plugins/my.plugin.PluginWithWarningAtExecTime.properties') << 'implementation-class=PluginWithWarningAtExecTime'

        succeeds('jar')
    }
}
