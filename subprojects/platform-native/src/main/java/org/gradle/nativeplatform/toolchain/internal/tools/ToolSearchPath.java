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

package org.gradle.nativeplatform.toolchain.internal.tools;

import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.toolchain.internal.ToolType;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolSearchPath {
    private final Map<String, File> executables = new HashMap<String, File>();
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
        File executable = executables.get(exeName);
        if (executable == null) {
            executable = findExecutable(operatingSystem, exeName);
            if (executable != null) {
                executables.put(exeName, executable);
            }
        }
        return executable == null || !executable.isFile() ? new MissingTool(key, exeName, pathEntries) : new FoundTool(executable);
    }

    private File findExecutable(OperatingSystem operatingSystem, String name) {
        List<File> path = pathEntries.isEmpty() ? operatingSystem.getPath() : pathEntries;
        String exeName = operatingSystem.getExecutableName(name);
        try {
            if (name.contains(File.separator)) {
                return maybeResolveFile(operatingSystem, new File(name), new File(exeName));
            }
            for (File pathEntry : path) {
                File resolved = maybeResolveFile(operatingSystem, new File(pathEntry, name), new File(pathEntry, exeName));
                if (resolved != null) {
                    return resolved;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return null;
    }

    private File maybeResolveFile(OperatingSystem operatingSystem, File symlinkCandidate, File exeCandidate) throws IOException {
        if (exeCandidate.isFile()) {
            return exeCandidate;
        }
        if (operatingSystem.isWindows()) {
            File symlink = maybeResolveCygwinSymlink(symlinkCandidate);
            if (symlink != null) {
                return symlink;
            }
        }
        return null;
    }

    private File maybeResolveCygwinSymlink(File symlink) throws IOException {
        if (!symlink.isFile()) {
            return null;
        }
        if (symlink.length() <= 11) {
            return null;
        }

        String pathStr;
        DataInputStream instr = new DataInputStream(new BufferedInputStream(new FileInputStream(symlink)));
        try {
            byte[] header = new byte[10];
            instr.readFully(header);
            if (!new String(header, "utf-8").equals("!<symlink>")) {
                return null;
            }
            byte[] pathContent = new byte[(int) symlink.length() - 11];
            instr.readFully(pathContent);
            pathStr = new String(pathContent, "utf-8");
        } finally {
            instr.close();
        }

        symlink = new File(symlink.getParentFile(), pathStr);
        if (symlink.isFile()) {
            return symlink.getCanonicalFile();
        }
        return null;
    }

    private static class FoundTool implements CommandLineToolSearchResult {
        private final File tool;

        private FoundTool(File tool) {
            this.tool = tool;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public File getTool() {
            return tool;
        }

        @Override
        public void explain(DiagnosticsVisitor visitor) {
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

        @Override
        public void explain(DiagnosticsVisitor visitor) {
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

        @Override
        public File getTool() {
            TreeFormatter formatter = new TreeFormatter();
            explain(formatter);
            throw new GradleException(formatter.toString());
        }

        @Override
        public boolean isAvailable() {
            return false;
        }
    }
}
