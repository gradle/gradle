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

import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType;

import java.io.Serializable;

/**
 * Information about an annotation processor, based on its static metadata
 * in <code>META-INF/services/javax.annotation.processing.Processor</code> and
 * <code>META-INF/gradle/incremental.annotation.processors</code>
 */
public class AnnotationProcessorDeclaration implements Serializable {
    private final String className;
    private final IncrementalAnnotationProcessorType type;

    public AnnotationProcessorDeclaration(String className, IncrementalAnnotationProcessorType type) {
        this.className = className;
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public IncrementalAnnotationProcessorType getType() {
        return type;
    }

    @Override
    public String toString() {
        return className + " (type: " + type + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AnnotationProcessorDeclaration that = (AnnotationProcessorDeclaration) o;

        if (!className.equals(that.className)) {
            return false;
        }
        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = className.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }
}
