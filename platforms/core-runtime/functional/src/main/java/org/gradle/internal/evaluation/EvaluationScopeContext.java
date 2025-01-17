/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.evaluation;

import javax.annotation.Nullable;

/**
 * A scope context. One can obtain an instance by calling {@link EvaluationContext#open(EvaluationOwner)}.
 * Closing this context removes the owner from the evaluation context.
 * The primary use case is to serve as an argument for the try-with-resources block.
 * It can also serve as a token parameter of a method that must be executed within such a block.
 * <p>
 * It is not safe to call {@link #close()} multiple times.
 * The instances of the class may not be unique for different owners being added.
 * <p>
 * Contexts must be closed in the order they are obtained.
 * This context must be closed by the same thread that obtained it.
 */
public interface EvaluationScopeContext extends AutoCloseable {
    /**
     * Returns the owner of the current scope, which is the last object that started its evaluation.
     * Can be null if the current scope has no owner (e.g. a just opened nested context).
     *
     * @return the owner
     */
    @Nullable
    EvaluationOwner getOwner();

    /**
     * Opens a nested context. A nested context allows to re-enter evaluation of the objects that are being evaluated in this context.
     * The newly returned nested context has no owner.
     * <p>
     * This method may be marginally more effective than using {@link EvaluationContext#evaluateNested(ScopedEvaluation)}.
     *
     * @return the nested context, to close it when done
     */
    EvaluationScopeContext nested();

    /**
     * Removes the owner added to evaluation context when obtaining this class from the context.
     * Must be called exactly once for every {@link EvaluationContext#open(EvaluationOwner)} or {@link #nested()} call.
     * Prefer to use try-with-resource instead of calling this method.
     */
    @Override
    void close();
}
