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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;

import javax.annotation.Nullable;

public class CompositePropertyVisitor implements PropertyVisitor {
    private final PropertyVisitor[] visitors;

    public CompositePropertyVisitor(PropertyVisitor... visitors) {
        this.visitors = visitors;
    }

    @Override
    public void visitInputFileProperty(
        String propertyName,
        boolean optional,
        boolean skipWhenEmpty,
        DirectorySensitivity directorySensitivity,
        LineEndingSensitivity lineEndingSensitivity,
        boolean incremental,
        @Nullable Class<? extends FileNormalizer> fileNormalizer,
        PropertyValue value,
        InputFilePropertyType filePropertyType
    ) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitInputFileProperty(
                propertyName,
                optional,
                skipWhenEmpty,
                directorySensitivity,
                lineEndingSensitivity,
                incremental,
                fileNormalizer,
                value,
                filePropertyType
            );
        }
    }

    @Override
    public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitInputProperty(propertyName, value, optional);
        }
    }

    @Override
    public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitOutputFileProperty(propertyName, optional, value, filePropertyType);
        }
    }

    @Override
    public void visitDestroyableProperty(Object value) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitDestroyableProperty(value);
        }
    }

    @Override
    public void visitLocalStateProperty(Object value) {
        for (PropertyVisitor visitor : visitors) {
            visitor.visitLocalStateProperty(value);
        }
    }
}
