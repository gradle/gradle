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
package org.gradle.api.internal.plugins;

import org.apache.tools.ant.taskdefs.Chmod;
import org.gradle.api.UncheckedIOException;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.gradle.util.AntUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

public class StartScriptGenerator {
    /**
     * The display name of the application
     */
    private String applicationName;

    /**
     * The environment variable to use to provide additional options to the JVM
     */
    private String optsEnvironmentVar;

    /**
     * The environment variable to use to control exit value (windows only)
     */
    private String exitEnvironmentVar;

    private String mainClassName;

    private Iterable<String> defaultJvmOpts = new ArrayList<String>();

    /**
     * The classpath, relative to the application home directory.
     */
    private Iterable<String> classpath;

    /**
     * The path of the script, relative to the application home directory.
     */
    private String scriptRelPath;

    /**
     * This system property to use to pass the script name to the application. May be null.
     */
    private String appNameSystemProperty;

    private final ScriptGenerator unixStartScriptGenerator;
    private final ScriptGenerator windowsStartScriptGenerator;
    private final UnixFileOperation unixFileOperation;

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setOptsEnvironmentVar(String optsEnvironmentVar) {
        this.optsEnvironmentVar = optsEnvironmentVar;
    }

    public void setExitEnvironmentVar(String exitEnvironmentVar) {
        this.exitEnvironmentVar = exitEnvironmentVar;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    public void setDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        this.defaultJvmOpts = defaultJvmOpts;
    }

    public void setClasspath(Iterable<String> classpath) {
        this.classpath = classpath;
    }

    public void setScriptRelPath(String scriptRelPath) {
        this.scriptRelPath = scriptRelPath;
    }

    public void setAppNameSystemProperty(String appNameSystemProperty) {
        this.appNameSystemProperty = appNameSystemProperty;
    }

    public StartScriptGenerator() {
        this(new UnixStartScriptGenerator(), new WindowsStartScriptGenerator());
    }

    public StartScriptGenerator(ScriptGenerator unixStartScriptGenerator, ScriptGenerator windowsStartScriptGenerator) {
        this(unixStartScriptGenerator, windowsStartScriptGenerator, new AntUnixFileOperation());
    }

    private StartScriptGenerator(ScriptGenerator unixStartScriptGenerator, ScriptGenerator windowsStartScriptGenerator, UnixFileOperation unixFileOperation) {
        this.unixStartScriptGenerator = unixStartScriptGenerator;
        this.windowsStartScriptGenerator = windowsStartScriptGenerator;
        this.unixFileOperation = unixFileOperation;
    }


    private JavaAppStartScriptGenerationDetails createStartScriptGenerationDetails() {
        return new DefaultJavaAppStartScriptGenerationDetails(applicationName, optsEnvironmentVar, exitEnvironmentVar, mainClassName, defaultJvmOpts, classpath, scriptRelPath, appNameSystemProperty);
    }

    public void generateUnixScript(File unixScript) {
        unixStartScriptGenerator.generateScript(createStartScriptGenerationDetails(), createWriter(unixScript));
        unixFileOperation.createExecutablePermission(unixScript);
    }

    public void generateWindowsScript(File windowsScript) {
        windowsStartScriptGenerator.generateScript(createStartScriptGenerationDetails(), createWriter(windowsScript));
    }

    private Writer createWriter(File script) {
        try {
            return new FileWriter(script);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static interface UnixFileOperation {
        void createExecutablePermission(File file);
    }

    static class AntUnixFileOperation implements UnixFileOperation {
        public void createExecutablePermission(File file) {
            Chmod chmod = new Chmod();
            chmod.setFile(file);
            chmod.setPerm("ugo+rx");
            chmod.setProject(AntUtil.createProject());
            chmod.execute();
        }
    }
}
