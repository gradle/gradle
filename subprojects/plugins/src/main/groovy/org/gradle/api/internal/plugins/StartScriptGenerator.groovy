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

import org.apache.tools.ant.taskdefs.Chmod
import org.gradle.api.scripting.JavaAppStartScriptGenerationDetails
import org.gradle.api.scripting.ScriptGenerator
import org.gradle.util.AntUtil

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

    private final ScriptGenerator<JavaAppStartScriptGenerationDetails> unixStartScriptGenerator
    private final ScriptGenerator<JavaAppStartScriptGenerationDetails> windowsStartScriptGenerator
    private final UnixFileOperation unixFileOperation

    StartScriptGenerator() {
        this(new UnixStartScriptGenerator(), new WindowsStartScriptGenerator())
    }

    StartScriptGenerator(ScriptGenerator<JavaAppStartScriptGenerationDetails> unixStartScriptGenerator,
                         ScriptGenerator<JavaAppStartScriptGenerationDetails> windowsStartScriptGenerator) {
        this(unixStartScriptGenerator, windowsStartScriptGenerator, new AntUnixFileOperation())
    }

    StartScriptGenerator(ScriptGenerator<JavaAppStartScriptGenerationDetails> unixStartScriptGenerator,
                         ScriptGenerator<JavaAppStartScriptGenerationDetails> windowsStartScriptGenerator,
                         UnixFileOperation unixFileOperation) {
        this.unixStartScriptGenerator = unixStartScriptGenerator
        this.windowsStartScriptGenerator = windowsStartScriptGenerator
        this.unixFileOperation = unixFileOperation
    }


    private JavaAppStartScriptGenerationDetails createStartScriptGenerationDetails() {
        JavaAppStartScriptGenerationDetails scriptGenerationDetails = new JavaAppStartScriptGenerationDetails()
        scriptGenerationDetails.applicationName = applicationName
        scriptGenerationDetails.optsEnvironmentVar = optsEnvironmentVar
        scriptGenerationDetails.exitEnvironmentVar = exitEnvironmentVar
        scriptGenerationDetails.mainClassName = mainClassName
        scriptGenerationDetails.defaultJvmOpts = defaultJvmOpts
        scriptGenerationDetails.classpath = classpath
        scriptGenerationDetails.scriptRelPath = scriptRelPath
        scriptGenerationDetails.appNameSystemProperty = appNameSystemProperty
        scriptGenerationDetails
    }

    void generateUnixScript(File unixScript) {
        unixStartScriptGenerator.generateScript(createStartScriptGenerationDetails(), new FileWriter(unixScript))
        unixFileOperation.createExecutablePermission(unixScript)
    }

    void generateWindowsScript(File windowsScript) {
        windowsStartScriptGenerator.generateScript(createStartScriptGenerationDetails(), new FileWriter(windowsScript))
    }

    static interface UnixFileOperation {
        void createExecutablePermission(File file)
    }

    static class AntUnixFileOperation implements UnixFileOperation {
        void createExecutablePermission(File file) {
            Chmod chmod = new Chmod()
            chmod.file = file
            chmod.perm = "ugo+rx"
            chmod.project = AntUtil.createProject()
            chmod.execute()
        }
    }
}
