/*
 * Copyright 2009 the original author or authors.
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



package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.util.TestUtil
import spock.lang.Specification
import org.gradle.util.TextUtil

class StartScriptGeneratorAdapterTest extends Specification {

    public static final String WINDOWS_TEMPLATE_TEXT = StartScriptGenerator.getResource(ApplicationPlugin.DEFAULT_WINDOWS_TEMPLATE).getText()
    public static final String NIX_TEMPLATE_TEXT = StartScriptGenerator.getResource(ApplicationPlugin.DEFAULT_UNIX_TEMPLATE).getText()

    def "classpath for unix script uses slashes as path separator"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.setClasspath(project.files("Jar.jar"))
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.contains("CLASSPATH=\$APP_HOME/lib/Jar.jar")
    }


    def "unix script uses unix line separator"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.split(TextUtil.windowsLineSeparator).length == 1
        unixScriptContent.split(TextUtil.unixLineSeparator).length == 164
    }

    def "classpath for windows script uses backslash as path separator and windows line separator"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.setClasspath(project.files("Jar.jar"))
        ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter winAdapter = new ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter(new StringReader(WINDOWS_TEMPLATE_TEXT))
        winAdapter.setCreateStartScripts(startScripts)
        when:
        String windowsScriptContent = winAdapter.getText()
        then:
        windowsScriptContent.contains("set CLASSPATH=%APP_HOME%\\lib\\Jar.jar")
        windowsScriptContent.split(TextUtil.windowsLineSeparator).length == 90
    }

    def "windows script uses windows line separator"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter winAdapter = new ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter(new StringReader(WINDOWS_TEMPLATE_TEXT))
        winAdapter.setCreateStartScripts(startScripts)
        when:
        String windowsScriptContent = winAdapter.getText()
        then:
        windowsScriptContent.split(TextUtil.windowsLineSeparator).length == 90
    }

    def "defaultJvmOpts is expanded properly in windows script"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=bar', '-Xint']
        ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter winAdapter = new ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter(new StringReader(WINDOWS_TEMPLATE_TEXT))
        winAdapter.setCreateStartScripts(startScripts)
        when:
        String windowsScriptContent = winAdapter.getText()
        then:
        windowsScriptContent.contains('set DEFAULT_JVM_OPTS="-Dfoo=bar" "-Xint"')
    }

    def "defaultJvmOpts is expanded properly in windows script -- spaces"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=bar baz', '-Xint']
        ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter winAdapter = new ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter(new StringReader(WINDOWS_TEMPLATE_TEXT))
        winAdapter.setCreateStartScripts(startScripts)
        when:
        String windowsScriptContent = winAdapter.getText()
        then:
        windowsScriptContent.contains(/set DEFAULT_JVM_OPTS="-Dfoo=bar baz" "-Xint"/)
    }

    def "defaultJvmOpts is expanded properly in windows script -- double quotes"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=b"ar baz', '-Xi""nt', '-Xpatho\\"logical']
        ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter winAdapter = new ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter(new StringReader(WINDOWS_TEMPLATE_TEXT))
        winAdapter.setCreateStartScripts(startScripts)
        when:
        String windowsScriptContent = winAdapter.getText()
        then:
        windowsScriptContent.contains(/set DEFAULT_JVM_OPTS="-Dfoo=b\"ar baz" "-Xi\"\"nt" "-Xpatho\\\"logical"/)
    }

    def "defaultJvmOpts is expanded properly in windows script -- backslashes and shell metacharacters"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=b\\ar baz', '-Xint%PATH%']
        ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter winAdapter = new ApplicationPlugin.LazyWindowsStartScriptGeneratorAdapter(new StringReader(WINDOWS_TEMPLATE_TEXT))
        winAdapter.setCreateStartScripts(startScripts)
        when:
        String windowsScriptContent = winAdapter.getText()
        then:
        windowsScriptContent.contains(/set DEFAULT_JVM_OPTS="-Dfoo=b\ar baz" "-Xint%%PATH%%"/)
    }

    def "defaultJvmOpts is expanded properly in unix script"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=bar', '-Xint']
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.contains('DEFAULT_JVM_OPTS=\'"-Dfoo=bar" "-Xint"\'')
    }

    def "defaultJvmOpts is expanded properly in unix script -- spaces"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=bar baz', '-Xint']
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=bar baz" "-Xint"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- double quotes"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=b"ar baz', '-Xi""nt']
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\"ar baz" "-Xi\"\"nt"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- single quotes"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=b\'ar baz', '-Xi\'\'n`t']
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=b'"'"'ar baz" "-Xi'"'"''"'"'n'"`"'t"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- backslashes and shell metacharacters"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        startScripts.defaultJvmOpts = ['-Dfoo=b\\ar baz', '-Xint$PATH']
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\\ar baz" "-Xint/ + '\\$PATH' + /"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- empty list"() {
        given:
        Project project = TestUtil.createRootProject()
        ApplicationPlugin plugin = new ApplicationPlugin()
        plugin.apply(project)
        ApplicationPluginConvention pluginConvention = project.convention.plugins.application
        CreateStartScripts startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        pluginConvention.applicationName = "TestApp"
        ApplicationPlugin.LazyNixStartScriptGeneratorAdapter nixAdapter = new ApplicationPlugin.LazyNixStartScriptGeneratorAdapter(new StringReader(NIX_TEMPLATE_TEXT))
        nixAdapter.setCreateStartScripts(startScripts)
        when:
        String unixScriptContent = nixAdapter.getText()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS=""/)
    }
}
