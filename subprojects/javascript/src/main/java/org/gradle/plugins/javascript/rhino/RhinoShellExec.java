/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.rhino;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RhinoShellExec extends JavaExec {

    private List<Object> rhinoOptions = new LinkedList<Object>();
    private List<Object> scriptArgs = new LinkedList<Object>();
    private Object script;

    public RhinoShellExec() {
    }

    public List<String> getRhinoOptions() {
        return CollectionUtils.stringize(rhinoOptions);
    }

    public void setRhinoOptions(Object... rhinoOptions) {
        this.rhinoOptions = new LinkedList<Object>(Arrays.asList(rhinoOptions));
    }

    public void rhinoOptions(Object... rhinoOptions) {
        this.rhinoOptions.addAll(Arrays.asList(rhinoOptions));
    }

    public List<String> getScriptArgs() {
        return CollectionUtils.stringize(scriptArgs);
    }

    public void setScriptArgs(Object... scriptArgs) {
        this.scriptArgs = new LinkedList<Object>(Arrays.asList(scriptArgs));
    }

    public void scriptArgs(Object... scriptArgs) {
        this.scriptArgs.addAll(Arrays.asList(scriptArgs));
    }

    @InputFile
    @Optional
    public File getScript() {
        return script == null ? null : getProject().file(script);
    }

    public void setScript(Object script) {
        this.script = script;
    }

    @Override
    @Input
    public List<String> getArgs() {
        List<String> args = new ArrayList<String>(rhinoOptions.size() + 1 + scriptArgs.size());
        args.addAll(getRhinoOptions());
        File script = getScript();
        if (script != null) {
            args.add(script.getAbsolutePath());
        }
        args.addAll(getScriptArgs());
        return args;
    }

    @Override
    public JavaExec setArgs(Iterable<?> applicationArgs) {
        throw argsUnsupportOperationException();
    }

    @Override
    public JavaExec args(Object... args) {
        throw argsUnsupportOperationException();
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        throw argsUnsupportOperationException();
    }

    private UnsupportedOperationException argsUnsupportOperationException() {
        return new UnsupportedOperationException("Cannot set args directly on RhinoExec, use rhinoOptions, scriptArgs and/or script");
    }

    @Override
    public void exec() {
        super.setArgs(getArgs());
        super.exec();
    }
}
