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

package org.gradle.api.internal.artifacts.transform;

import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The invocation of a transformer on a primary input file.
 */
public class TransformerInvocation {
    private final Transformer transformer;
    private final File primaryInput;
    private final TransformationSubject subjectBeingTransformed;
    
    private List<File> result;
    private Exception failure;

    public TransformerInvocation(Transformer transformer, File primaryInput, TransformationSubject subjectBeingTransformed) {
        this.transformer = transformer;
        this.primaryInput = primaryInput;
        this.subjectBeingTransformed = subjectBeingTransformed;
    }

    public File getPrimaryInput() {
        return primaryInput;
    }

    public Transformer getTransformer() {
        return transformer;
    }

    public TransformationSubject getSubjectBeingTransformed() {
        return subjectBeingTransformed;
    }

    public void success(List<File> result) {
        this.result = result;
    }

    public void failure(Exception failure) {
        this.failure = failure;
    }

    public List<File> getResult() {
        return result;
    }

    @Nullable
    public Exception getFailure() {
        return failure;
    }
}
