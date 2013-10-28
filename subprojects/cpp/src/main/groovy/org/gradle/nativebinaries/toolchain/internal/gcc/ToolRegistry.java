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

package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.toolchain.internal.ToolType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {
    private final Map<ToolType, String> executableNames = new HashMap<ToolType, String>();
    private final Map<ToolType, CompositeArgAction> argTransformers = new HashMap<ToolType, CompositeArgAction>();
    private final Map<String, File> executables = new HashMap<String, File>();
    private final List<File> pathEntries = new ArrayList<File>();

    private final OperatingSystem operatingSystem;

    public ToolRegistry(OperatingSystem operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public List<File> getPath() {
        return pathEntries;
    }

    public void setPath(List<File> pathEntries) {
        this.pathEntries.clear();
        this.pathEntries.addAll(pathEntries);
        executables.clear();
    }

    public void path(File pathEntry) {
        pathEntries.add(pathEntry);
        executables.clear();
    }

    public String getExeName(ToolType key) {
        return executableNames.get(key);
    }

    public void setExeName(ToolType key, String name) {
        executableNames.put(key, name);
    }

    public File locate(ToolType key) {
        String exeName = executableNames.get(key);
        if (executables.containsKey(exeName)) {
            return executables.get(exeName);
        }
        File exe = findExecutable(operatingSystem, exeName);
        executables.put(exeName, exe);
        return exe;
    }

    protected File findExecutable(OperatingSystem operatingSystem, String name) {
        String exeName = operatingSystem.getExecutableName(name);
        for (File pathEntry : pathEntries) {
            File candidate = new File(pathEntry, exeName);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return operatingSystem.findInPath(name);
    }

    public CompositeArgAction getArgTransformer(ToolType toolType) {
        if (!argTransformers.containsKey(toolType)) {
            argTransformers.put(toolType, new CompositeArgAction());
        }
        return argTransformers.get(toolType);
    }

    public void addArgsAction(ToolType toolType, Action<List<String>> arguments) {
        getArgTransformer(toolType).add(arguments);
    }

    private static class CompositeArgAction implements Action<List<String>> {
        private final List<Action<List<String>>> actions = new ArrayList<Action<List<String>>>();

        public void add(Action<List<String>> action) {
            actions.add(action);
        }

        public void execute(List<String> args) {
            for (Action<List<String>> action : actions) {
                action.execute(args);
            }
        }

    }
}
