/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder;

import org.jspecify.annotations.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.gradle.internal.FileUtils.hasExtension;

public class DefaultScriptFileResolver implements ScriptFileResolver {

    private static final String[] EXTENSIONS = ScriptFileUtil.getValidExtensions();

    @Nullable
    private final ScriptFileResolvedListener scriptFileResolvedListener;

    public DefaultScriptFileResolver(@Nullable ScriptFileResolvedListener scriptFileResolvedListener) {
        this.scriptFileResolvedListener = scriptFileResolvedListener;
    }

    public DefaultScriptFileResolver() {
        this.scriptFileResolvedListener = null;
    }

    @Override
    @Nullable
    public File resolveScriptFile(File dir, String basename) {
        File selectedCandidate = null;
        List<File> ignoredCandidates = new ArrayList<>();

        for (String extension : EXTENSIONS) {
            File candidate = new File(dir, basename + extension);
            if (isCandidateFile(candidate)) {
                if (selectedCandidate == null) {
                    selectedCandidate = candidate;
                } else {
                    ignoredCandidates.add(candidate);
                }
            }
        }

        if (!ignoredCandidates.isEmpty()) {
            reportMultipleCandidates(dir, selectedCandidate, ignoredCandidates);
        }

        return selectedCandidate;
    }

    private static void reportMultipleCandidates(File dir, File selectedCandidate, List<File> ignoredCandidates) {
        // Unfortunately, we have to use the emitter holder here
        // Reason is that this class is used in a utility fashion rather as a service, and makes injection virtually impossible.
        // See the StartParameter for an example.
        ProblemsProgressEventEmitterHolder.get().getReporter().report(
            ProblemId.create("multiple-candidates", "Multiple script candidates", GradleCoreProblemGroup.script()),
            spec -> {
                spec.severity(Severity.WARNING);
                spec.contextualLabel(
                    String.format("Multiple script candidates were found in directory '%s'", dir.getAbsolutePath())
                );
                spec.fileLocation(dir.getAbsolutePath());
                spec.details(
                    " - Selected candidate: '" + selectedCandidate.getAbsolutePath() + "'" + System.lineSeparator() +
                        ignoredCandidates.stream()
                            .map(f -> " - Ignored candidate: '" + f.getAbsolutePath() + "'")
                            .collect(Collectors.joining(System.lineSeparator()))
                );
                spec.solution("Remove the ignored script files or rename them");
            }
        );
    }

    private boolean isCandidateFile(File candidate) {
        notifyListener(candidate);
        return candidate.isFile();
    }

    @Override
    @SuppressWarnings("MixedMutabilityReturnType")
    public List<File> findScriptsIn(File dir) {
        File[] candidates = dir.listFiles();
        if (candidates == null || candidates.length == 0) {
            return emptyList();
        }
        List<File> found = new ArrayList<File>(candidates.length);
        for (File candidate : candidates) {
            if (candidate.isFile() && hasScriptExtension(candidate)) {
                found.add(candidate);
            }
        }
        return found;
    }


    private void notifyListener(File scriptFile) {
        if (scriptFileResolvedListener != null) {
            scriptFileResolvedListener.onScriptFileResolved(scriptFile);
        }
    }

    private boolean hasScriptExtension(File file) {
        for (String extension : EXTENSIONS) {
            if (hasExtension(file, extension)) {
                return true;
            }
        }
        return false;
    }
}
