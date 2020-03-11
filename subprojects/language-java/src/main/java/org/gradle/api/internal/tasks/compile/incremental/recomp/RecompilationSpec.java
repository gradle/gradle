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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class RecompilationSpec {
    private final Collection<String> classesToCompile = new LinkedHashSet<>();
    private final Collection<String> classesToProcess = new LinkedHashSet<>();
    private final Collection<GeneratedResource> resourcesToGenerate = new LinkedHashSet<>();
    private final Set<String> relativeSourcePathsToCompile = new LinkedHashSet<>();
    private String fullRebuildCause;

    @Override
    public String toString() {
        return "RecompilationSpec{" +
            "classesToCompile=" + classesToCompile +
            ", classesToProcess=" + classesToProcess +
            ", resourcesToGenerate=" + resourcesToGenerate +
            ", relativeSourcePathsToCompile=" + relativeSourcePathsToCompile +
            ", fullRebuildCause='" + fullRebuildCause + '\'' +
            ", buildNeeded=" + isBuildNeeded() +
            ", fullRebuildNeeded=" + isFullRebuildNeeded() +
            '}';
    }

    public Collection<String> getClassesToCompile() {
        return classesToCompile;
    }

    /**
     * @return the relative paths of files we clearly know to recompile
     */
    public Set<String> getRelativeSourcePathsToCompile() {
        return relativeSourcePathsToCompile;
    }

    public Collection<String> getClassesToProcess() {
        return classesToProcess;
    }

    public Collection<GeneratedResource> getResourcesToGenerate() {
        return resourcesToGenerate;
    }

    public boolean isBuildNeeded() {
        return isFullRebuildNeeded() || !classesToCompile.isEmpty() || !classesToProcess.isEmpty() || !relativeSourcePathsToCompile.isEmpty();
    }

    public boolean isFullRebuildNeeded() {
        return fullRebuildCause != null;
    }

    public String getFullRebuildCause() {
        return fullRebuildCause;
    }

    public void setFullRebuildCause(String description, File file) {
        fullRebuildCause = description != null ? description : "'" + file.getName() + "' was changed";
    }

}
