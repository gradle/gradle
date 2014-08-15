/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.Action;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultCommandLineToolInvocation implements MutableCommandLineToolInvocation {
    private final List<Action<List<String>>> postArgsActions = new ArrayList<Action<List<String>>>();
    private final Map<String, String> environment = new HashMap<String, String>();
    private final List<File> path = new ArrayList<File>();
    private List<String> args = new ArrayList<String>();
    private File workDirectory;

    public MutableCommandLineToolInvocation copy() {
        DefaultCommandLineToolInvocation invocation = new DefaultCommandLineToolInvocation();
        for (Action<List<String>> postArgsAction : postArgsActions) {
            invocation.addPostArgsAction(postArgsAction);
        }
        invocation.args.addAll(this.args);
        invocation.workDirectory = this.workDirectory;
        invocation.path.addAll(this.path);
        invocation.environment.putAll(environment);
        return invocation;
    }

    public void addPostArgsAction(Action<List<String>> postArgsAction) {
        postArgsActions.add(postArgsAction);
    }

    public List<String> getArgs() {
        List<String> transformedArgs = Lists.newArrayList(args);
        for (Action<List<String>> postArgsAction : postArgsActions) {
            postArgsAction.execute(transformedArgs);
        }
        return transformedArgs;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public File getWorkDirectory() {
        return workDirectory;
    }

    public void setWorkDirectory(File workDirectory) {
        this.workDirectory = workDirectory;
    }

    public void addPath(File pathEntry) {
        this.path.add(pathEntry);
    }

    public void addPath(List<File> path) {
        this.path.addAll(path);
    }

    public List<File> getPath() {
        return path;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void addEnvironmentVar(String key, String value) {
        this.environment.put(key, value);
    }
}
