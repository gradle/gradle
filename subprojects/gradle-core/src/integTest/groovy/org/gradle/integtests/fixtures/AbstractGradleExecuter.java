/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.gradle.util.Jvm;

import java.io.File;
import java.util.*;

public abstract class AbstractGradleExecuter implements GradleExecuter {
    private final List<String> args = new ArrayList<String>();
    private final List<String> tasks = new ArrayList<String>();
    private File workingDir;
    private boolean quiet;
    private boolean taskList;
    private boolean searchUpwards;
    private Map<String, String> environmentVars = new HashMap<String, String>();
    private String executable;
    private File userHomeDir;
    private File buildScript;

    public GradleExecuter reset() {
        args.clear();
        tasks.clear();
        workingDir = null;
        buildScript = null;
        quiet = false;
        taskList = false;
        searchUpwards = false;
        executable = null;
        userHomeDir = null;
        environmentVars.clear();
        return this;
    }

    public boolean worksWith(Jvm jvm) {
        return jvm.isJava5Compatible();
    }

    public GradleExecuter inDirectory(File directory) {
        workingDir = directory;
        return this;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    protected void copyTo(GradleExecuter executer) {
        if (workingDir != null) {
            executer.inDirectory(workingDir);
        }
        if (buildScript != null) {
            executer.usingBuildScript(buildScript);
        }
        executer.withTasks(tasks);
        executer.withArguments(args);
        executer.withEnvironmentVars(environmentVars);
        executer.usingExecutable(executable);
        if (quiet) {
            executer.withQuietLogging();
        }
        if (taskList) {
            executer.withTaskList();
        }
        executer.withUserHomeDir(userHomeDir);
    }

    public GradleExecuter usingBuildScript(File buildScript) {
        this.buildScript = buildScript;
        return this;
    }

    public GradleExecuter usingBuildScript(String scriptText) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingSettingsFile(File settingsFile) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingInitScript(File initScript) {
        throw new UnsupportedOperationException();
    }

    public File getUserHomeDir() {
        return userHomeDir;
    }

    public GradleExecuter withUserHomeDir(File userHomeDir) {
        this.userHomeDir = userHomeDir;
        return this;
    }

    public GradleExecuter usingExecutable(String script) {
        this.executable = script;
        return this;
    }

    public String getExecutable() {
        return executable;
    }

    public GradleExecuter withSearchUpwards() {
        searchUpwards = true;
        return this;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public GradleExecuter withQuietLogging() {
        quiet = true;
        return this;
    }

    public GradleExecuter withTaskList() {
        taskList = true;
        return this;
    }

    public GradleExecuter withDependencyList() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withArguments(String... args) {
        return withArguments(Arrays.asList(args));
    }

    public GradleExecuter withArguments(List<String> args) {
        this.args.clear();
        this.args.addAll(args);
        return this;
    }

    public GradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        environmentVars.clear();
        for (Map.Entry<String, ?> entry : environment.entrySet()) {
            environmentVars.put(entry.getKey(), entry.getValue().toString());
        }
        return this;
    }

    public Map<String, String> getEnvironmentVars() {
        return environmentVars;
    }

    public GradleExecuter withTasks(String... names) {
        return withTasks(Arrays.asList(names));
    }

    public GradleExecuter withTasks(List<String> names) {
        tasks.clear();
        tasks.addAll(names);
        return this;
    }

    protected List<String> getAllArgs() {
        List<String> allArgs = new ArrayList<String>();
        if (buildScript != null) {
            allArgs.add("--build-file");
            allArgs.add(buildScript.getAbsolutePath());
        }
        if (quiet) {
            allArgs.add("--quiet");
        }
        if (taskList) {
            allArgs.add("tasks");
        }
        if (!searchUpwards) {
            allArgs.add("--no-search-upward");
        }
        if (userHomeDir != null) {
            args.add("--gradle-user-home");
            args.add(userHomeDir.getAbsolutePath());
        }
        allArgs.addAll(args);
        allArgs.addAll(tasks);
        return allArgs;
    }

    public final ExecutionResult run() {
        try {
            return doRun();
        } finally {
            reset();
        }
    }

    public final ExecutionFailure runWithFailure() {
        try {
            return doRunWithFailure();
        } finally {
            reset();
        }
    }

    protected abstract ExecutionResult doRun();

    protected abstract ExecutionFailure doRunWithFailure();
}
