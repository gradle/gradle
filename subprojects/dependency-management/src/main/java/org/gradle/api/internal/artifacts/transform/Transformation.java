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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.Try;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

/**
 * The internal API equivalent of {@link org.gradle.api.artifacts.transform.TransformAction}, which is also aware of our cache infrastructure.
 *
 * This can encapsulate a single transformation step using a single transformer or a chain of transformation steps.
 */
public interface Transformation extends Describable {

    boolean endsWith(Transformation otherTransform);

    int stepsCount();

    /**
     * Starts transforming the given subject, using the given work queue to run asynchronous work. The result is passed to the given handler on completion.
     */
    void startTransformation(TransformationSubject subjectToTransform, ExecutionGraphDependenciesResolver dependenciesResolver, NodeExecutionContext context, boolean isTopLevel, BuildOperationQueue<RunnableBuildOperation> workQueue, ResultReceiver resultReceiver);

    /**
     * Whether the transformation requires dependencies of the transformed artifact to be injected.
     */
    boolean requiresDependencies();

    /**
     * Extract the transformation steps from this transformation.
     */
    void visitTransformationSteps(Action<? super TransformationStep> action);

    interface ResultReceiver {
        void completed(TransformationSubject source, Try<TransformationSubject> result);
    }
}
