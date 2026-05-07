/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.scripts;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ScriptResolutionResult {

    /**
     * The directory in which the script was searched for.
     */
    private final File directory;

    /**
     * The base name of the script.
     * E.g. `build`, `script`, or `init`
     */
    private final String basename;

    /**
     * The selected script file, or {@code null} if no script file was found.
     */
    @Nullable
    private final File selectedCandidate;

    /**
     * The list of ignored script files.
     * Empty if no other script files were found.
     */
    private final List<File> ignoredCandidates;

    public ScriptResolutionResult(File directory, String basename, @Nullable File selectedCandidate, List<File> ignoredCandidates) {
        this.directory = directory;
        this.basename = basename;
        this.selectedCandidate = selectedCandidate;
        this.ignoredCandidates = Collections.unmodifiableList(ignoredCandidates);
    }

    @Nullable
    public File getSelectedCandidate() {
        return selectedCandidate;
    }

    public boolean isScriptFound() {
        return selectedCandidate != null;
    }

    public List<File> getIgnoredCandidates() {
        return ignoredCandidates;
    }

    public String getBasename() {
        return basename;
    }

    public File getDirectory() {
        return directory;
    }

    public static ScriptResolutionResult fromSingleFile(String basename, File scriptFile) {
        Objects.requireNonNull(basename);
        Objects.requireNonNull(scriptFile);
        return new ScriptResolutionResult(scriptFile.getParentFile(), basename, scriptFile, Collections.emptyList());
    }
}
