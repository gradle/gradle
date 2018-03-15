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

package org.gradle.api.internal.tasks.compile.processing;

/**
 * The different kinds of annotation processors that the incremental compiler knows how to handle.
 *
 * <p>
 * In order to be treated incrementally, annotation processors need to use the originating element
 * API provided by {@link javax.annotation.processing.Filer#createSourceFile(CharSequence, javax.lang.model.element.Element...)}
 * and {@link javax.annotation.processing.Filer#createClassFile(CharSequence, javax.lang.model.element.Element...)}.
 * Depending on how this API is used by the processor, it can be categorized as either SINGLE_ORIGIN or MULTIPLE_ORIGIN.
 * </p>
 *
 * <p>
 * Processors that want to use {@link javax.annotation.processing.Filer#createResource(javax.tools.JavaFileManager.Location, CharSequence, CharSequence, javax.lang.model.element.Element...)}
 * or {@link javax.annotation.processing.Filer#getResource(javax.tools.JavaFileManager.Location, CharSequence, CharSequence)}
 * are unsupported.
 * </p>
 *
 * <p>
 * Processors can register themselves as incremental by providing a <code>META-INF/gradle/incremental.annotation.processors</code> file,
 * which has one line per processor, each line containing the fully qualified class name and one of the processor types listed
 * here, separated by a comma. E.g.:
 *
 * <pre>
 *     com.my.processor.MyProcessor,SINGLE_ORIGIN
 *     com.my.other.OtherProcessor,MULTIPLE_ORIGIN
 * </pre>
 *
 * </p>
 */
public enum IncrementalAnnotationProcessorType {
    /**
     * A processor whose generated files have exactly one originating element.
     */
    SINGLE_ORIGIN(true),
    /**
     * A processor whose generated files can have zero to many originating elements.
     */
    MULTIPLE_ORIGIN(true),
    /**
     * Any other kind of processor.
     */
    UNKNOWN(false);

    private final boolean incremental;

    IncrementalAnnotationProcessorType(boolean incremental) {
        this.incremental = incremental;
    }

    public boolean isIncremental() {
        return incremental;
    }
}
