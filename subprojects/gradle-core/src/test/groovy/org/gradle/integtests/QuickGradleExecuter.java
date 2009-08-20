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

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class QuickGradleExecuter extends AbstractGradleExecuter {
    private final GradleDistribution dist;
    private File directory;
    private List<String> tasks;

    public QuickGradleExecuter(GradleDistribution dist) {
        this.dist = dist;
    }

    @Override
    public GradleExecuter inDirectory(File directory) {
        this.directory = directory;
        return this;
    }

    @Override
    public GradleExecuter withTasks(List<String> names) {
        this.tasks = new ArrayList<String>(names);
        return this;
    }

    public ExecutionResult run() {
        return configureExecuter().run();
    }

    public ExecutionFailure runWithFailure() {
        return configureExecuter().runWithFailure();
    }

    private GradleExecuter configureExecuter() {
        StartParameter parameter = new StartParameter();
        parameter.setLogLevel(LogLevel.INFO);
        parameter.setGradleHomeDir(dist.getGradleHomeDir());
        System.setProperty("gradle.home", dist.getGradleHomeDir().getAbsolutePath());

        GradleExecuter executer = new InProcessGradleExecuter(parameter);
        if (directory != null) {
            executer.inDirectory(directory);
        }
        if (!tasks.isEmpty()) {
            executer.withTasks(tasks);
        }
        return executer;
    }
}
