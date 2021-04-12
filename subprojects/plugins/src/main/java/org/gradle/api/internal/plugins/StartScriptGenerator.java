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
import org.gradle.api.Action;
import org.gradle.internal.IoActions;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.gradle.util.internal.AntUtil;
import org.gradle.util.internal.CollectionUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.util.Collections;

public class StartScriptGenerator {

    private String applicationName;
    private String optsEnvironmentVar;
    private String exitEnvironmentVar;
    private String mainClassName;
    private Iterable<String> defaultJvmOpts = Collections.emptyList();
    private Iterable<String> classpath;
    private Iterable<String> modulePath = Collections.emptyList();
    private String scriptRelPath;
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

    public void setModulePath(Iterable<String> modulePath) {
        this.modulePath = modulePath;
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

    StartScriptGenerator(ScriptGenerator unixStartScriptGenerator, ScriptGenerator windowsStartScriptGenerator, UnixFileOperation unixFileOperation) {
        this.unixStartScriptGenerator = unixStartScriptGenerator;
        this.windowsStartScriptGenerator = windowsStartScriptGenerator;
        this.unixFileOperation = unixFileOperation;
    }

    private JavaAppStartScriptGenerationDetails createStartScriptGenerationDetails() {
        return new DefaultJavaAppStartScriptGenerationDetails(
                applicationName,
                optsEnvironmentVar,
                exitEnvironmentVar,
                mainClassName,
                CollectionUtils.toStringList(defaultJvmOpts),
                CollectionUtils.toStringList(classpath),
                CollectionUtils.toStringList(modulePath),
                scriptRelPath,
                appNameSystemProperty
        );
    }

    public void generateUnixScript(final File unixScript) {
        IoActions.writeTextFile(unixScript, new Generate(createStartScriptGenerationDetails(), unixStartScriptGenerator));
        unixFileOperation.createExecutablePermission(unixScript);
    }

    public void generateWindowsScript(File windowsScript) {
        IoActions.writeTextFile(windowsScript, new Generate(createStartScriptGenerationDetails(), windowsStartScriptGenerator));
    }

    interface UnixFileOperation {
        void createExecutablePermission(File file);
    }

    static class AntUnixFileOperation implements UnixFileOperation {
        @Override
        public void createExecutablePermission(File file) {
            Chmod chmod = new Chmod();
            chmod.setFile(file);
            chmod.setPerm("ugo+rx");
            chmod.setProject(AntUtil.createProject());
            chmod.execute();
        }
    }

    private static class Generate implements Action<BufferedWriter> {
        private final JavaAppStartScriptGenerationDetails startScriptGenerationDetails;
        private final ScriptGenerator unixStartScriptGenerator;

        public Generate(JavaAppStartScriptGenerationDetails startScriptGenerationDetails, ScriptGenerator unixStartScriptGenerator) {
            this.startScriptGenerationDetails = startScriptGenerationDetails;
            this.unixStartScriptGenerator = unixStartScriptGenerator;
        }

        @Override
        public void execute(BufferedWriter writer) {
            unixStartScriptGenerator.generateScript(startScriptGenerationDetails, writer);
        }
    }
}
