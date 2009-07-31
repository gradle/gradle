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
import java.util.Arrays;
import java.util.Map;
import java.util.List;

public abstract class AbstractGradleExecuter implements GradleExecuter {
    public GradleExecuter inDirectory(File directory) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingBuildScript(String script) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingSettingsFile(File settingsFile) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingExecutable(String script) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withSearchUpwards() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withQuietLogging() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withTaskList() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withDependencyList() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withArguments(String... args) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withTasks(String... names) {
        return withTasks(Arrays.asList(names));
    }

    public GradleExecuter withTasks(List<String> names) {
        throw new UnsupportedOperationException();
    }
}
