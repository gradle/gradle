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

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.gradle.internal.FileUtils.hasExtension;

public class DefaultScriptFileResolver implements ScriptFileResolver {

    private static final String[] EXTENSIONS = ScriptFileUtil.getValidExtensions();

    @Nullable
    private final ScriptFileResolverListeners listeners;

    public DefaultScriptFileResolver(@Nullable ScriptFileResolverListeners listeners) {
        this.listeners = listeners;
    }

    public DefaultScriptFileResolver() {
        this(null);
    }

    @Override
    public ScriptResolutionResult resolveScriptFile(File dir, String basename) {
        File selectedCandidate = null;
        List<File> ignoredCandidates = new ArrayList<>();

        for (String extension : EXTENSIONS) {
            File candidate = new File(dir, basename + extension);
            if (selectedCandidate == null) {
                notifyListener(candidate);
                if (candidate.isFile()) {
                    selectedCandidate = candidate;
                }
            } else {
                if (candidate.isFile()) {
                    ignoredCandidates.add(candidate);
                }
            }
        }

        return new ScriptResolutionResult(dir, basename, selectedCandidate, ignoredCandidates);
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
        if (listeners != null) {
            listeners.onScriptFileResolved(scriptFile);
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
