/*
 * Copyright 2011 the original author or authors.
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

import groovy.text.SimpleTemplateEngine
import org.gradle.util.TextUtil
import org.gradle.util.AntUtil
import org.apache.tools.ant.taskdefs.Chmod

class StartScriptGenerator {
    /** The display name of the application     */
    String applicationName

    /** The environment variable to use to provide additional options to the JVM     */
    String optsEnvironmentVar

    /** The environment variable to use to control exit value (windows only)     */
    String exitEnvironmentVar

    String mainClassName

    /** The classpath, relative to the application home directory     */
    Iterable<String> classpath

    /** The path of the script, relative to the application home directory    */
    String scriptRelPath

    /** This system property to use to pass the script name to the application. May be null     */
    String appNameSystemProperty

    private final engine = new SimpleTemplateEngine()

    void generateUnixScript(File unixScript) {
        def unixClassPath = classpath.collect { "\$APP_HOME/$it" }.join(":")
        def binding = [applicationName: applicationName,
                optsEnvironmentVar: optsEnvironmentVar,
                mainClassName: mainClassName,
                appNameSystemProperty: appNameSystemProperty,
                appHomeRelativePath: appHomeRelativePath,
                classpath: unixClassPath]
        generateScript('unixStartScript.txt', binding, TextUtil.unixLineSeparator, unixScript)
        createExecutablePermission(unixScript)
    }

    void generateWindowsScript(File windowsScript) {
        def windowsClassPath = classpath.collect { "%APP_HOME%\\${it.replace('/', '\\')}" }.join(";")
        def appHome = appHomeRelativePath.replace('/', '\\')
        def binding = [applicationName: applicationName,
                optsEnvironmentVar: optsEnvironmentVar,
                exitEnvironmentVar: exitEnvironmentVar,
                mainClassName: mainClassName,
                appNameSystemProperty: appNameSystemProperty,
                appHomeRelativePath: appHome,
                classpath: windowsClassPath]
        generateScript('windowsStartScript.txt', binding, TextUtil.windowsLineSeparator, windowsScript)
    }

    private void createExecutablePermission(File unixScriptFile) {
        Chmod chmod = new Chmod()
        chmod.file = unixScriptFile
        chmod.perm = "ugo+rx"
        chmod.project = AntUtil.createProject()
        chmod.execute()
    }

    private void generateScript(String templateName, Map binding, String lineSeparator, File outputFile) {
        def stream = StartScriptGenerator.getResource(templateName)
        def templateText = stream.text
        def output = engine.createTemplate(templateText).make(binding)
        def nativeOutput = TextUtil.convertLineSeparators(output as String, lineSeparator)
        outputFile.parentFile.mkdirs()
        outputFile.write(nativeOutput)
    }

    private String getAppHomeRelativePath() {
        def depth = scriptRelPath.count("/")
        if (depth == 0) {
            return ""
        }
        return (1..depth).collect {".."}.join("/")
    }
}
