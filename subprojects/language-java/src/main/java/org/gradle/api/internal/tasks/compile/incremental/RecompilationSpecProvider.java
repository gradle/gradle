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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.Action;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarChangeProcessor;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshot;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshot;
import org.gradle.api.internal.tasks.compile.incremental.jar.PreviousCompilation;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.file.FileType;
import org.gradle.internal.util.Alignment;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.FileUtils.hasExtension;

public class RecompilationSpecProvider {

    private final SourceToNameConverter sourceToNameConverter;
    private final FileOperations fileOperations;

    public RecompilationSpecProvider(SourceToNameConverter sourceToNameConverter, FileOperations fileOperations) {
        this.sourceToNameConverter = sourceToNameConverter;
        this.fileOperations = fileOperations;
    }

    public RecompilationSpec provideRecompilationSpec(IncrementalTaskInputs inputs, PreviousCompilation previousCompilation, JarClasspathSnapshot jarClasspathSnapshot) {
        //creating an action that will be executed against all changes
        RecompilationSpec spec = new RecompilationSpec();
        JarChangeProcessor jarChangeProcessor = new JarChangeProcessor(fileOperations, jarClasspathSnapshot, previousCompilation);
        processJarChanges(previousCompilation.getJarSnapshots(), jarClasspathSnapshot, jarChangeProcessor, spec);
        JavaChangeProcessor javaChangeProcessor = new JavaChangeProcessor(previousCompilation, sourceToNameConverter);
        ClassChangeProcessor classChangeProcessor = new ClassChangeProcessor(previousCompilation);
        InputChangeAction action = new InputChangeAction(spec, javaChangeProcessor, classChangeProcessor);

        //go!
        inputs.outOfDate(action);
        if (action.spec.getFullRebuildCause() != null) {
            //short circuit in case we already know that that full rebuild is needed
            return action.spec;
        }
        inputs.removed(action);
        return action.spec;
    }

    private void processJarChanges(Map<File, JarSnapshot> previousCompilationJarSnapshots, JarClasspathSnapshot currentJarSnapshots, JarChangeProcessor jarChangeProcessor, RecompilationSpec spec) {
        Set<File> previousCompilationJars = previousCompilationJarSnapshots.keySet();
        Set<File> currentCompilationJars = currentJarSnapshots.getJars();
        List<Alignment<File>> alignment = Alignment.align(currentCompilationJars.toArray(new File[0]), previousCompilationJars.toArray(new File[0]));
        for (Alignment<File> fileAlignment : alignment) {
            switch (fileAlignment.getKind()) {
                case added:
                    jarChangeProcessor.processChange(FileChange.added(fileAlignment.getCurrentValue().getAbsolutePath(), "jar", FileType.RegularFile), spec);
                    break;
                case removed:
                    jarChangeProcessor.processChange(FileChange.removed(fileAlignment.getPreviousValue().getAbsolutePath(), "jar", FileType.RegularFile), spec);
                    break;
                case transformed:
                    // If we detect a transformation in the classpath, we need to recompile, because we could typically be facing the case where
                    // 2 jars are reversed in the order of classpath elements, and one class that was shadowing the other is now visible
                    spec.setFullRebuildCause("Classpath has been changed", null);
                    return;
                case identical:
                    File key = fileAlignment.getPreviousValue();
                    JarSnapshot previousSnapshot = previousCompilationJarSnapshots.get(key);
                    JarSnapshot snapshot = currentJarSnapshots.getSnapshot(key);
                    if (!snapshot.getHash().equals(previousSnapshot.getHash())) {
                        jarChangeProcessor.processChange(FileChange.modified(key.getAbsolutePath(), "jar", FileType.RegularFile, FileType.RegularFile), spec);
                    }
                    break;
            }
        }
    }

    private static class InputChangeAction implements Action<InputFileDetails> {
        private final RecompilationSpec spec;
        private final JavaChangeProcessor javaChangeProcessor;
        private final ClassChangeProcessor classChangeProcessor;

        public InputChangeAction(RecompilationSpec spec, JavaChangeProcessor javaChangeProcessor, ClassChangeProcessor classChangeProcessor) {
            this.spec = spec;
            this.javaChangeProcessor = javaChangeProcessor;
            this.classChangeProcessor = classChangeProcessor;
        }

        @Override
        public void execute(InputFileDetails input) {
            if (spec.getFullRebuildCause() != null) {
                return;
            }
            if (hasExtension(input.getFile(), ".java")) {
                javaChangeProcessor.processChange(input, spec);
            } else if (hasExtension(input.getFile(), ".class")) {
                classChangeProcessor.processChange(input, spec);
            }
        }
    }
}
