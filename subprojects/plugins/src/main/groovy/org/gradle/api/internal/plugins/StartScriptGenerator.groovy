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
import org.gradle.util.GFileUtils

class StartScriptGenerator {
    /**
     * The display name of the application
     */
    String applicationName

    /**
     * The environment variable to use to provide additional options to the JVM
     */
    String optsEnvironmentVar

    /**
     * The environment variable to use to control exit value (windows only)
     */
    String exitEnvironmentVar

    String mainClassName

    Iterable<String> defaultJvmOpts = []

    /**
     * The classpath, relative to the application home directory.
     */
    Iterable<String> classpath

    /**
     * The path of the script, relative to the application home directory.
     */
    String scriptRelPath

    /**
     * This system property to use to pass the script name to the application. May be null.
     */
    String appNameSystemProperty

    private final engine = new SimpleTemplateEngine()

    void generateUnixScript(File unixScript) {
        String nativeOutput = generateUnixScriptContent()
        writeToFile(nativeOutput, unixScript)
        createExecutablePermission(unixScript)
    }

    String generateUnixScriptContent() {
        def unixClassPath = classpath.collect { "\$APP_HOME/${it.replace('\\', '/')}" }.join(":")
        def quotedDefaultJvmOpts = defaultJvmOpts.collect{
            //quote ', ", \, $. Probably not perfect. TODO: identify non-working cases, fail-fast on them
            it = it.replace('\\', '\\\\')
            it = it.replace('"', '\\"')
            it = it.replace(/'/, /'"'"'/)
            it = it.replace(/`/, /'"`"'/)
            it = it.replace('$', '\\$')
            (/"${it}"/)
        }
        //put the whole arguments string in single quotes, unless defaultJvmOpts was empty,
        // in which case we output "" to stay compatible with existing builds that scan the script for it
        def defaultJvmOptsString = (quotedDefaultJvmOpts ? /'${quotedDefaultJvmOpts.join(' ')}'/ : '""')
        def binding = [applicationName: applicationName,
                optsEnvironmentVar: optsEnvironmentVar,
                mainClassName: mainClassName,
                defaultJvmOpts: defaultJvmOptsString,
                appNameSystemProperty: appNameSystemProperty,
                appHomeRelativePath: appHomeRelativePath,
                classpath: unixClassPath]
        return generateNativeOutput('unixStartScript.txt', binding, TextUtil.unixLineSeparator)
    }

    void generateWindowsScript(File windowsScript) {
        String nativeOutput = generateWindowsScriptContent()
        writeToFile(nativeOutput, windowsScript);
    }

    String generateWindowsScriptContent() {
        def windowsClassPath = classpath.collect { "%APP_HOME%\\${it.replace('/', '\\')}" }.join(";")
        def appHome = appHomeRelativePath.replace('/', '\\')
        //argument quoting:
        // - " must be encoded as \"
        // - % must be encoded as %%
        // - pathological case: \" must be encoded as \\\", but other than that, \ MUST NOT be quoted
        // - other characters (including ') will not be quoted
        // - use a state machine rather than regexps
        def quotedDefaultJvmOpts = defaultJvmOpts.collect {
            def wasOnBackslash = false
            it = it.collect { ch ->
                def repl = ch
                if (ch == '%') {
                    repl = '%%'
                } else if (ch == '"') {
                    repl = (wasOnBackslash ? '\\' : '') + '\\"'
                }
                wasOnBackslash = (ch == '\\')
                repl
            }
            (/"${it.join()}"/)
        }
        def defaultJvmOptsString = quotedDefaultJvmOpts.join(' ')
        def binding = [applicationName: applicationName,
                optsEnvironmentVar: optsEnvironmentVar,
                exitEnvironmentVar: exitEnvironmentVar,
                mainClassName: mainClassName,
                defaultJvmOpts: defaultJvmOptsString,
                appNameSystemProperty: appNameSystemProperty,
                appHomeRelativePath: appHome,
                classpath: windowsClassPath]
        return generateNativeOutput('windowsStartScript.txt', binding, TextUtil.windowsLineSeparator)

    }

    private void createExecutablePermission(File unixScriptFile) {
        Chmod chmod = new Chmod()
        chmod.file = unixScriptFile
        chmod.perm = "ugo+rx"
        chmod.project = AntUtil.createProject()
        chmod.execute()
    }

    void writeToFile(String scriptContent, File scriptFile) {
        GFileUtils.mkdirs(scriptFile.parentFile)
        scriptFile.write(scriptContent)
    }


    private String generateNativeOutput(String templateName, Map binding, String lineSeparator) {
        def stream = StartScriptGenerator.getResource(templateName)
        def templateText = stream.text
        def output = engine.createTemplate(templateText).make(binding)
        def nativeOutput = TextUtil.convertLineSeparators(output as String, lineSeparator)
        return nativeOutput;

    }

    private String getAppHomeRelativePath() {
        def depth = scriptRelPath.count("/")
        if (depth == 0) {
            return ""
        }
        return (1..depth).collect {".."}.join("/")
    }
}
