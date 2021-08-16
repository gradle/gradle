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

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class RecompilationSpec {
    private final Set<String> classesToCompile = new LinkedHashSet<>();
    private final Collection<String> classesToProcess = new LinkedHashSet<>();
    private final Collection<GeneratedResource> resourcesToGenerate = new LinkedHashSet<>();
    private final PreviousCompilation previousCompilation;
    private String fullRebuildCause;

    public RecompilationSpec(PreviousCompilation previousCompilation) {
        this.previousCompilation = previousCompilation;
    }

    @Override
    public String toString() {
        return "RecompilationSpec{" +
            "classesToCompile=" + classesToCompile +
            ", classesToProcess=" + classesToProcess +
            ", resourcesToGenerate=" + resourcesToGenerate +
            ", fullRebuildCause='" + fullRebuildCause + '\'' +
            ", buildNeeded=" + isBuildNeeded() +
            ", fullRebuildNeeded=" + isFullRebuildNeeded() +
            '}';
    }

    public void addClassesToCompile(Collection<String> classes) {
        classesToCompile.addAll(classes);
    }

    public Set<String> getClassesToCompile() {
        return Collections.unmodifiableSet(classesToCompile);
    }

    public PreviousCompilation getPreviousCompilation() {
        return previousCompilation;
    }

    public void addClassesToProcess(Collection<String> classes) {
        classes.forEach(classToReprocess -> {
            if (classToReprocess.endsWith("package-info") || classToReprocess.equals("module-info")) {
                classesToCompile.add(classToReprocess);
            } else {
                classesToProcess.add(classToReprocess);
            }
        });
    }

    public Collection<String> getClassesToProcess() {
        return Collections.unmodifiableCollection(classesToProcess);
    }

    public void addResourcesToGenerate(Collection<GeneratedResource> resources) {
        resourcesToGenerate.addAll(resources);
    }

    public Collection<GeneratedResource> getResourcesToGenerate() {
        return Collections.unmodifiableCollection(resourcesToGenerate);
    }

    public boolean isBuildNeeded() {
        return isFullRebuildNeeded() || !classesToCompile.isEmpty() || !classesToProcess.isEmpty();
    }

    public boolean isFullRebuildNeeded() {
        return fullRebuildCause != null;
    }

    public String getFullRebuildCause() {
        return fullRebuildCause;
    }

    public void setFullRebuildCause(String description) {
        fullRebuildCause = description;
    }

}
