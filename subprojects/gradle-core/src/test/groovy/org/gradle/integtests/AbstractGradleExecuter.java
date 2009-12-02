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
package org.gradle.integtests;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.List;

public abstract class AbstractGradleExecuter implements GradleExecuter {
    private final List<String> args = new ArrayList<String>();
    private final List<String> tasks = new ArrayList<String>();
    private File workingDir;
    private boolean quiet;
    private boolean taskList;
    private boolean searchUpwards = false;
    private boolean disableTestGradleUserHome = false;

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
        executer.withTasks(tasks);
        executer.withArguments(args);
        if (quiet) {
            executer.withQuietLogging();
        }
        if (taskList) {
            executer.withTaskList();
        }
    }

    public boolean isDisableTestGradleUserHome() {
        return disableTestGradleUserHome;
    }

    public void setDisableTestGradleUserHome(boolean disableTestGradleUserHome) {
        this.disableTestGradleUserHome = disableTestGradleUserHome;
    }

    public GradleExecuter usingBuildScript(String script) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingSettingsFile(File settingsFile) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingInitScript(File initScript) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingExecutable(String script) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
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
        if (quiet) {
            allArgs.add("--quiet");
        }
        if (taskList) {
            allArgs.add("--tasks");
        }
        if (!searchUpwards) {
            allArgs.add("--no-search-upward");
        }
        allArgs.addAll(args);
        allArgs.addAll(tasks);
        return allArgs;
    }
}
