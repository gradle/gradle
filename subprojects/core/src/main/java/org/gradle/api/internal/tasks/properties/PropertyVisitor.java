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

import org.gradle.api.internal.tasks.TaskDestroyablePropertySpec;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskInputPropertySpec;
import org.gradle.api.internal.tasks.TaskLocalStatePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;

/**
 * Visits properties of beans which are inputs, outputs, destroyables or local state.
 */
public interface PropertyVisitor {

    void visitInputFileProperty(TaskInputFilePropertySpec inputFileProperty);

    void visitInputProperty(TaskInputPropertySpec inputProperty);

    void visitOutputFileProperty(TaskOutputFilePropertySpec outputFileProperty);

    void visitDestroyableProperty(TaskDestroyablePropertySpec destroyableProperty);

    void visitLocalStateProperty(TaskLocalStatePropertySpec localStateProperty);

    class Adapter implements PropertyVisitor {

        @Override
        public void visitInputFileProperty(TaskInputFilePropertySpec inputFileProperty) {
        }

        @Override
        public void visitInputProperty(TaskInputPropertySpec inputProperty) {
        }

        @Override
        public void visitOutputFileProperty(TaskOutputFilePropertySpec outputFileProperty) {
        }

        @Override
        public void visitDestroyableProperty(TaskDestroyablePropertySpec destroyableProperty) {
        }

        @Override
        public void visitLocalStateProperty(TaskLocalStatePropertySpec localStateProperty) {
        }
    }
}
