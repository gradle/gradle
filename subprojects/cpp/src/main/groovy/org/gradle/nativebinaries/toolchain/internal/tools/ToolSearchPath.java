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

package org.gradle.nativebinaries.toolchain.internal.tools;

import org.gradle.api.GradleException;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.gcc.CommandLineToolSearchResult;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolSearchPath {
    private final Map<String, CommandLineToolSearchResult> executables = new HashMap<String, CommandLineToolSearchResult>();
    private final List<File> pathEntries = new ArrayList<File>();

    private final OperatingSystem operatingSystem;

    public ToolSearchPath(OperatingSystem operatingSystem) {
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

    public CommandLineToolSearchResult locate(ToolType key, String exeName) {
        CommandLineToolSearchResult result = executables.get(exeName);
        if (result == null) {
            File exe = findExecutable(operatingSystem, exeName);
            result = exe == null || !exe.isFile() ? new MissingTool(key, exeName, pathEntries) : new FoundTool(exe);
            executables.put(exeName, result);
        }
        return result;
    }

    protected File findExecutable(OperatingSystem operatingSystem, String name) {
        if (!pathEntries.isEmpty()) {
            String exeName = operatingSystem.getExecutableName(name);
            for (File pathEntry : pathEntries) {
                File candidate = new File(pathEntry, exeName);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
            return null;
        } else {
            return operatingSystem.findInPath(name);
        }
    }

    private static class FoundTool implements CommandLineToolSearchResult {
        private final File tool;

        private FoundTool(File tool) {
            this.tool = tool;
        }

        public boolean isAvailable() {
            return true;
        }

        public File getTool() {
            return tool;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private static class MissingTool implements CommandLineToolSearchResult {
        private final ToolType type;
        private final String exeName;
        private final List<File> path;

        private MissingTool(ToolType type, String exeName, List<File> path) {
            this.type = type;
            this.exeName = exeName;
            this.path = path;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            if (path.isEmpty()) {
                visitor.node(String.format("Could not find %s '%s' in system path.", type.getToolName(), exeName));
            } else {
                visitor.node(String.format("Could not find %s '%s'. Searched in", type.getToolName(), exeName));
                visitor.startChildren();
                for (File location : path) {
                    visitor.node(location.toString());
                }
                visitor.endChildren();
            }
        }

        public File getTool() {
            TreeFormatter formatter = new TreeFormatter();
            explain(formatter);
            throw new GradleException(formatter.toString());
        }

        public boolean isAvailable() {
            return false;
        }
    }
}
