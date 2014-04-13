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
package org.gradle.nativebinaries.toolchain.internal;

import com.beust.jcommander.internal.Lists;
import org.gradle.api.Action;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultCommandLineToolInvocation implements MutableCommandLineToolInvocation {
    private final List<Action<List<String>>> postArgsActions = new ArrayList<Action<List<String>>>();
    private List<String> args = new ArrayList<String>();
    private File workDirectory;

    public MutableCommandLineToolInvocation copy() {
        DefaultCommandLineToolInvocation invocation = new DefaultCommandLineToolInvocation();
        for (Action<List<String>> postArgsAction : postArgsActions) {
            invocation.addPostArgsAction(postArgsAction);
        }
        invocation.setArgs(getArgs());
        invocation.setWorkDirectory(getWorkDirectory());
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
}
