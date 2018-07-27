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

import static org.gradle.internal.FileUtils.hasExtension;

class InputChangeAction implements Action<InputFileDetails> {
    private final RecompilationSpec spec;
    private final JavaChangeProcessor javaChangeProcessor;
    private final AnnotationProcessorChangeProcessor annotationProcessorChangeProcessor;

    InputChangeAction(RecompilationSpec spec, JavaChangeProcessor javaChangeProcessor, AnnotationProcessorChangeProcessor annotationProcessorChangeProcessor) {
        this.spec = spec;
        this.javaChangeProcessor = javaChangeProcessor;
        this.annotationProcessorChangeProcessor = annotationProcessorChangeProcessor;
    }

    @Override
    public void execute(InputFileDetails input) {
        if (spec.getFullRebuildCause() != null) {
            return;
        }

        annotationProcessorChangeProcessor.processChange(input, spec);

        if (hasExtension(input.getFile(), ".java")) {
            javaChangeProcessor.processChange(input, spec);
        }
    }
}
