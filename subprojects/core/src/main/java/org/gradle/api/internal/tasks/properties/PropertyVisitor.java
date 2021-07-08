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
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;

/**
 * Visits properties of beans which are inputs, outputs, destroyables or local state.
 */
@UsedByScanPlugin("test-distribution")
public interface PropertyVisitor {
    void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType);

    void visitInputProperty(String propertyName, PropertyValue value, boolean optional);

    void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType);

    void visitDestroyableProperty(Object value);

    void visitLocalStateProperty(Object value);

    @UsedByScanPlugin("test-distribution")
    class Adapter implements PropertyVisitor {
        @UsedByScanPlugin("test-distribution")
        @Override
        public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
        }

        @Override
        public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
        }

        @Override
        @UsedByScanPlugin("test-distribution")
        public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
        }

        @Override
        public void visitDestroyableProperty(Object value) {
        }

        @Override
        public void visitLocalStateProperty(Object value) {
        }
    }
}
