/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.internal.operations.logging.BuildOperationLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultMutableCommandLineToolContext implements MutableCommandLineToolContext {
    private Action<List<String>> postArgsAction = Actions.doNothing();
    private final Map<String, String> environment = new HashMap<String, String>();
    private final List<File> path = new ArrayList<File>();

    @Override
    public void setArgAction(Action<List<String>> argAction) {
        postArgsAction = argAction;
    }

    @Override
    public void addPath(File pathEntry) {
        this.path.add(pathEntry);
    }

    @Override
    public void addPath(List<File> path) {
        this.path.addAll(path);
    }

    @Override
    public List<File> getPath() {
        return path;
    }

    @Override
    public Map<String, String> getEnvironment() {
        return environment;
    }

    @Override
    public Action<List<String>> getArgAction() {
        return postArgsAction;
    }

    @Override
    public void addEnvironmentVar(String key, String value) {
        this.environment.put(key, value);
    }

    @Override
    public CommandLineToolInvocation createInvocation(String description, File workDirectory, Iterable<String> args, BuildOperationLogger oplogger) {
        return new DefaultCommandLineToolInvocation(description, workDirectory, args, this, oplogger);
    }

    @Override
    public CommandLineToolInvocation createInvocation(String description, Iterable<String> args, BuildOperationLogger oplogger) {
        File currentWorkingDirectory = null;
        return createInvocation(description, currentWorkingDirectory, args, oplogger);
    }
}
