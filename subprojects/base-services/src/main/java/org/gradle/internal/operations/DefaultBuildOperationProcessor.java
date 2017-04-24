/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.GradleThread;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.util.CollectionUtils;

import java.util.List;

/**
 * This class delegates worker leases management to {@link org.gradle.internal.work.DefaultWorkerLeaseService} and require that a current operation is in flight.
 */
public class DefaultBuildOperationProcessor implements BuildOperationProcessor, Stoppable {
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final StoppableExecutor fixedSizePool;

    public DefaultBuildOperationProcessor(BuildOperationExecutor buildOperationExecutor, BuildOperationQueueFactory buildOperationQueueFactory, ExecutorFactory executorFactory, int maxWorkerCount) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildOperationQueueFactory = buildOperationQueueFactory;
        this.fixedSizePool = executorFactory.create("build operations", maxWorkerCount);
    }

    @Override
    public <T extends BuildOperation> void run(BuildOperationWorker<T> worker, Action<BuildOperationQueue<T>> generator) {
        doRun(worker, generator);
    }

    private <T extends BuildOperation> void doRun(BuildOperationWorker<T> worker, Action<BuildOperationQueue<T>> generator) {
        BuildOperationQueue<T> queue = buildOperationQueueFactory.create(fixedSizePool, new ParentBuildOperationAwareWorker<T>(worker));

        List<GradleException> failures = Lists.newArrayList();
        try {
            generator.execute(queue);
        } catch (Exception e) {
            failures.add(new BuildOperationQueueFailure("There was a failure while populating the build operation queue: " + e.getMessage(), e));
            queue.cancel();
        }

        try {
            queue.waitForCompletion();
        } catch (MultipleBuildOperationFailures e) {
            failures.add(e);
        }

        if (failures.size() == 1) {
            throw failures.get(0);
        } else if (failures.size() > 1) {
            throw new DefaultMultiCauseException(formatMultipleFailureMessage(failures), failures);
        }
    }

    @Override
    public <T extends RunnableBuildOperation> void run(final Action<BuildOperationQueue<T>> generator) {
        BuildOperationWorker<T> runnableWorker = new BuildOperationWorker<T>() {
            @Override
            public String getDisplayName() {
                return "runnable worker";
            }

            @Override
            public void execute(T t) {
                t.run();
            }
        };
        run(runnableWorker, generator);
    }

    public void stop() {
        fixedSizePool.stop();
    }

    private static String formatMultipleFailureMessage(List<GradleException> failures) {
        return StringUtils.join(CollectionUtils.collect(failures, new Transformer<String, GradleException>() {
            @Override
            public String transform(GradleException e) {
                return e.getMessage();
            }
        }), LINE_SEPARATOR + "AND" + LINE_SEPARATOR);
    }

    private class ParentBuildOperationAwareWorker<T extends BuildOperation> implements BuildOperationWorker<T> {
        private final BuildOperationExecutor.Operation parentOperation;
        private final BuildOperationWorker<T> delegate;

        private ParentBuildOperationAwareWorker(BuildOperationWorker<T> delegate) {
            this.parentOperation = GradleThread.isManaged() ? buildOperationExecutor.getCurrentOperation() : null;
            this.delegate = delegate;
        }

        @Override
        public String getDisplayName() {
            return delegate.getDisplayName();
        }

        @Override
        public void execute(final T t) {
            buildOperationExecutor.run(createBuildOperationDetails(t), new Action<BuildOperationContext>() {
                @Override
                public void execute(BuildOperationContext buildOperationContext) {
                    delegate.execute(t);
                }
            });
        }

        private BuildOperationDetails createBuildOperationDetails(T buildOperation) {
            BuildOperationDetails.Builder builder = BuildOperationDetails.displayName(buildOperation.getDescription());
            if (buildOperation instanceof DescribableBuildOperation) {
                DescribableBuildOperation describableBuildOperation = (DescribableBuildOperation) buildOperation;
                Object operationDescriptor = describableBuildOperation.getOperationDescriptor();
                builder.operationDescriptor(operationDescriptor);
                String displayName = describableBuildOperation.getProgressDisplayName();
                builder.progressDisplayName(displayName);
            }
            return builder.parent(parentOperation).build();
        }
    }
}
