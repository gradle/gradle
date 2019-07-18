/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.util.Collection;

import static org.gradle.internal.FileUtils.hasExtension;

class InputChangeAction implements Action<InputFileDetails> {
    private final RecompilationSpec spec;
    private final SourceFileChangeProcessor javaChangeProcessor;
    private final AnnotationProcessorChangeProcessor annotationProcessorChangeProcessor;
    private final ResourceChangeProcessor resourceChangeProcessor;
    private final JavaConventionalSourceFileClassNameConverter sourceFileClassNameConverter;

    InputChangeAction(RecompilationSpec spec,
                      SourceFileChangeProcessor javaChangeProcessor,
                      AnnotationProcessorChangeProcessor annotationProcessorChangeProcessor,
                      ResourceChangeProcessor resourceChangeProcessor,
                      JavaConventionalSourceFileClassNameConverter sourceFileClassNameConverter) {
        this.spec = spec;
        this.javaChangeProcessor = javaChangeProcessor;
        this.annotationProcessorChangeProcessor = annotationProcessorChangeProcessor;
        this.resourceChangeProcessor = resourceChangeProcessor;
        this.sourceFileClassNameConverter = sourceFileClassNameConverter;
    }

    @Override
    public void execute(InputFileDetails input) {
        if (spec.getFullRebuildCause() != null) {
            return;
        }

        File file = input.getFile();
        if (hasExtension(file, ".java")) {
            Collection<String> classNames = sourceFileClassNameConverter.getClassNames(file);
            if (classNames.isEmpty()) {
                // https://github.com/gradle/gradle/issues/9380
                // Remove a srcDir from a sourceSet
                spec.setFullRebuildCause("source dirs are changed", file);
            } else {
                javaChangeProcessor.processChange(file, classNames, spec);
            }
        } else if (hasExtension(file, ".jar") || hasExtension(file, ".class")) {
            annotationProcessorChangeProcessor.processChange(input, spec);
        } else {
            resourceChangeProcessor.processChange(input, spec);
        }
    }
}
