/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.progress;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.operations.BuildOperation;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.BuildOperationWorker;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A BuildOperationExecutor for tests.
 * Simply execute given operations, does not support current/parent operations.
 */
public class TestBuildOperationExecutor implements BuildOperationExecutor {

    public final BuildOperationLog log = new BuildOperationLog();

    @Override
    public BuildOperationState getCurrentOperation() {
        return new BuildOperationState() {
            @Override
            public Object getId() {
                return new OperationIdentifier(0);
            }

            @Override
            public Object getParentId() {
                return null;
            }
        };
    }

    public List<BuildOperationDescriptor> getOperations() {
        return log.getDescriptors();
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        log.run(buildOperation);
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        return log.call(buildOperation);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> generator) {
        generator.execute(new TestBuildOperationQueue<O>(log));
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        throw new UnsupportedOperationException();
    }


    private static class TestBuildOperationContext implements BuildOperationContext {

        private Object result;

        @Override
        public void failed(@Nullable Throwable failure) {
        }

        @Override
        public void setResult(@Nullable Object result) {
            this.result = result;
        }
    }

    public static class TestBuildOperationQueue<O extends RunnableBuildOperation> implements BuildOperationQueue<O> {

        public final BuildOperationLog log;

        public TestBuildOperationQueue() {
            this(new BuildOperationLog());
        }

        public TestBuildOperationQueue(BuildOperationLog log) {
            this.log = log;
        }

        @Override
        public void add(O operation) {
            log.run(operation);
        }

        @Override
        public void cancel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void waitForCompletion() throws MultipleBuildOperationFailures {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLogLocation(String logLocation) {
            throw new UnsupportedOperationException();
        }
    }

    public static class BuildOperationLog {
        private final List<Record> records = new CopyOnWriteArrayList<Record>();

        public static class Entry<R, D extends BuildOperationDetails<R>> {
            public final BuildOperationDescriptor descriptor;
            public final D details;
            public final R result;

            private Entry(BuildOperationDescriptor descriptor, D details, R result) {
                this.descriptor = descriptor;
                this.details = details;
                this.result = result;
            }
        }

        public List<BuildOperationDescriptor> getDescriptors() {
            return Lists.transform(new ArrayList<Record>(records), new Function<Record, BuildOperationDescriptor>() {
                @Override
                public BuildOperationDescriptor apply(@javax.annotation.Nullable Record input) {
                    return input.descriptor;
                }
            });
        }

        private <D extends BuildOperationDetails<?>> Record mostRecent(Class<D> detailsType) {
            ImmutableList<Record> copy = ImmutableList.copyOf(this.records).reverse();
            for (Record record : copy) {
                Object details = record.descriptor.getDetails();
                if (detailsType.isInstance(details)) {
                    return record;
                }
            }

            throw new AssertionError("Did not find operation with details of type: " + detailsType.getName());
        }

        public <D extends BuildOperationDetails<?>> D mostRecentDetails(Class<D> detailsType) {
            return detailsType.cast(mostRecent(detailsType));
        }

        public <R, D extends BuildOperationDetails<R>> R mostRecentResult(Class<D> detailsType) {
            Record record = mostRecent(detailsType);
            Object result = record.result;

            Class<R> resultType = extractResultType(detailsType);
            if (resultType.isInstance(result)) {
                return resultType.cast(result);
            } else {
                throw new AssertionError("Expected result type " + resultType.getName() + ", got " + result.getClass().getName());
            }
        }

        public <D extends BuildOperationDetails<?>> Throwable mostRecentFailure(Class<D> detailsType) {
            return mostRecent(detailsType).failure;
        }

        private static <R, D extends BuildOperationDetails<R>> Class<R> extractResultType(Class<D> detailsType) {
            return Cast.uncheckedCast(new TypeToken<R>(detailsType) {
            }.getRawType());
        }

        private static class Record {

            public final BuildOperationDescriptor descriptor;

            public Object result;
            public Throwable failure;

            private Record(BuildOperationDescriptor descriptor) {
                this.descriptor = descriptor;
            }

            <R, D extends BuildOperationDetails<R>> Entry<R, D> toEntry(Class<D> detailsType) {
                D details = detailsType.cast(descriptor.getDetails());
                R castResult = Cast.uncheckedCast(result);
                return new Entry<R, D>(descriptor, details, castResult);
            }

        }

        private void run(RunnableBuildOperation buildOperation) {
            Record record = new Record(buildOperation.description().build());
            records.add(record);
            TestBuildOperationContext context = new TestBuildOperationContext();
            try {
                buildOperation.run(context);
            } catch (Throwable failure) {
                record.failure = failure;
                throw UncheckedException.throwAsUncheckedException(failure);
            }
            record.result = context.result;
        }

        private <T> T call(CallableBuildOperation<T> buildOperation) {
            Record record = new Record(buildOperation.description().build());
            records.add(record);
            TestBuildOperationContext context = new TestBuildOperationContext();
            T t;
            try {
                t = buildOperation.call(context);
            } catch (Throwable failure) {
                record.failure = failure;
                throw UncheckedException.throwAsUncheckedException(failure);
            }
            record.result = context.result;
            return t;
        }

    }


}
