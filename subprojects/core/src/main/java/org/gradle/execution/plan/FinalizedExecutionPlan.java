/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.api.Describable;

import javax.annotation.concurrent.ThreadSafe;
import java.io.Closeable;

/**
 * An execution plan that has been finalized and can no longer be mutated.
 */
@ThreadSafe
public interface FinalizedExecutionPlan extends Describable, Closeable {
    FinalizedExecutionPlan EMPTY = new FinalizedExecutionPlan() {
        @Override
        public WorkSource<Node> asWorkSource() {
            throw new IllegalStateException();
        }

        @Override
        public QueryableExecutionPlan getContents() {
            return QueryableExecutionPlan.EMPTY;
        }

        @Override
        public String getDisplayName() {
            return "empty";
        }

        @Override
        public void close() {
        }
    };

    /**
     * Returns this plan as a {@link WorkSource} ready for execution.
     */
    WorkSource<Node> asWorkSource();

    /**
     * Returns the immutable contents of this plan.
     */
    QueryableExecutionPlan getContents();

    /**
     * Overridden to remove IOException.
     */
    @Override
    void close();
}
