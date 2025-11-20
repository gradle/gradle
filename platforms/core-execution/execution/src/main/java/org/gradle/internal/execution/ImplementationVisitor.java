/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

// TODO Move this to {@link InputVisitor}
public interface ImplementationVisitor {
    /**
     * Visit the implementation of the work. This is the type of the task or transform being executed.
     */
    void visitImplementation(Class<?> implementation);

    /**
     * Visits additional implementations related to the work. This is only used for additional task
     * actions that are added via {@code Task.doLast()} etc.
     */
    // TODO Make these additional implementations into immutable inputs
    void visitAdditionalImplementation(ImplementationSnapshot implementation);
}
