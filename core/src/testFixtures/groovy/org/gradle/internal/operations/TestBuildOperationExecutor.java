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

package org.gradle.internal.operations;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A BuildOperationExecutor for tests.
 * Simply execute given operations, does not support current/parent operations.
 */
public class TestBuildOperationExecutor implements BuildOperationExecutor {

    public final Log log = new Log();

    @Override
    public BuildOperationRef getCurrentOperation() {
        return new BuildOperationRef() {
            @Override
            public OperationIdentifier getId() {
                return new OperationIdentifier(1L);
            }

            @Override
            public OperationIdentifier getParentId() {
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
    public <O extends BuildOperation> void execute(O buildOperation, BuildOperationWorker<O> worker, @Nullable BuildOperationState defaultParent) {
        log.execute(buildOperation, worker);
    }

    @Override
    public BuildOperationContext start(BuildOperationDescriptor.Builder descriptor) {
        return log.start(descriptor);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        schedulingAction.execute(new TestBuildOperationQueue<O>(log));
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction) {
        runAllWithAccessToProjectState(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        runAll(schedulingAction);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(worker, schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        throw new UnsupportedOperationException();
    }

    private static class TestBuildOperationContext implements BuildOperationContext {

        private final Log.Record record;

        public TestBuildOperationContext(Log.Record record) {
            this.record = record;
        }

        @Override
        public void failed(@Nullable Throwable failure) {
            this.record.failure = failure;
        }

        @Override
        public void setResult(@Nullable Object result) {
            this.record.result = result;
        }

        @Override
        public void setStatus(String status) {
        }

        @Override
        public void progress(String status) {
        }

        @Override
        public void progress(long progress, long total, String units, String status) {
        }
    }

    public static class TestBuildOperationQueue<O extends RunnableBuildOperation> implements BuildOperationQueue<O> {

        public final Log log;

        public TestBuildOperationQueue() {
            this(new Log());
        }

        private TestBuildOperationQueue(Log log) {
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

    public static class Log {
        public final Deque<Record> records = new LinkedBlockingDeque<Record>();

        public List<BuildOperationDescriptor> getDescriptors() {
            return Lists.transform(new ArrayList<Record>(records), new Function<Record, BuildOperationDescriptor>() {
                @Override
                public BuildOperationDescriptor apply(Record input) {
                    return input.descriptor;
                }
            });
        }

        public <D, R, T extends BuildOperationType<D, R>> TypedRecord<D, R> mostRecent(Class<T> type) {
            Class<D> detailsType = BuildOperationTypes.detailsType(type);
            Iterator<Record> iterator = records.descendingIterator();
            while (iterator.hasNext()) {
                Record record = iterator.next();
                Object details = record.descriptor.getDetails();
                if (detailsType.isInstance(details)) {
                    return record.asTyped(type);
                }
            }

            throw new AssertionError("Did not find operation with details of type: " + detailsType.getName());
        }

        public <D, R, T extends BuildOperationType<D, R>> List<TypedRecord<D, R>> all(final Class<T> type) {
            final Class<D> detailsType = BuildOperationTypes.detailsType(type);
            return FluentIterable.from(records)
                .filter(new Predicate<Record>() {
                    @Override
                    public boolean apply(Record input) {
                        return detailsType.isInstance(input.descriptor.getDetails());
                    }
                })
                .transform(new Function<Record, TypedRecord<D, R>>() {
                    @Override
                    public TypedRecord<D, R> apply(Record input) {
                        return input.asTyped(type);
                    }
                })
                .toList();
        }

        public <R, D, T extends BuildOperationType<D, R>> D mostRecentDetails(Class<T> type) {
            return mostRecent(type).details;
        }

        public <R, D, T extends BuildOperationType<D, R>> R mostRecentResult(Class<T> type) {
            return mostRecent(type).result;
        }

        public <D, R, T extends BuildOperationType<D, R>> Throwable mostRecentFailure(Class<T> type) {
            return mostRecent(type).failure;
        }

        @Override
        public String toString() {
            return records.toString();
        }

        public static class Record {

            public final BuildOperationDescriptor descriptor;

            public Object result;
            public Throwable failure;

            private Record(BuildOperationDescriptor descriptor) {
                this.descriptor = descriptor;
            }

            @Override
            public String toString() {
                return descriptor.getDisplayName();
            }

            private <D, R, T extends BuildOperationType<D, R>> TypedRecord<D, R> asTyped(Class<? extends T> buildOperationType) {
                if (descriptor.getDetails() == null) {
                    throw new IllegalStateException("operation has null details");
                }

                return new TypedRecord<D, R>(
                    descriptor,
                    BuildOperationTypes.detailsType(buildOperationType).cast(descriptor.getDetails()),
                    BuildOperationTypes.resultType(buildOperationType).cast(result),
                    failure
                );
            }

        }

        public static class TypedRecord<D, R> {

            public final BuildOperationDescriptor descriptor;
            public final D details;
            public final R result;
            public final Throwable failure;

            private TypedRecord(BuildOperationDescriptor descriptor, D details, R result, Throwable failure) {
                this.descriptor = descriptor;
                this.details = details;
                this.result = result;
                this.failure = failure;
            }

            @Override
            public String toString() {
                return descriptor.getDisplayName();
            }
        }


        private void run(RunnableBuildOperation buildOperation) {
            Record record = new Record(buildOperation.description().build());
            records.add(record);
            TestBuildOperationContext context = new TestBuildOperationContext(record);
            try {
                buildOperation.run(context);
            } catch (Throwable failure) {
                if (record.failure == null) {
                    record.failure = failure;
                }
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }

        private <T> T call(CallableBuildOperation<T> buildOperation) {
            Record record = new Record(buildOperation.description().build());
            records.add(record);
            TestBuildOperationContext context = new TestBuildOperationContext(record);
            T t;
            try {
                t = buildOperation.call(context);
            } catch (Throwable failure) {
                if (record.failure == null) {
                    record.failure = failure;
                }
                throw UncheckedException.throwAsUncheckedException(failure);
            }
            return t;
        }

        private <O extends BuildOperation> void execute(O buildOperation, BuildOperationWorker<O> worker) {
            Record record = new Record(buildOperation.description().build());
            records.add(record);
            TestBuildOperationContext context = new TestBuildOperationContext(record);
            try {
                worker.execute(buildOperation, context);
            } catch (Throwable failure) {
                if (record.failure == null) {
                    record.failure = failure;
                }
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }

        private BuildOperationContext start(final BuildOperationDescriptor.Builder descriptor) {
            Record record = new Record(descriptor.build());
            records.add(record);
            final TestBuildOperationContext context = new TestBuildOperationContext(record);
            return new BuildOperationContext() {
                @Override
                public void failed(@Nullable Throwable failure) {
                    context.failed(failure);
                }

                @Override
                public void setResult(@Nullable Object result) {
                    context.setResult(result);
                }

                @Override
                public void setStatus(String status) {
                    context.setStatus(status);
                }

                @Override
                public void progress(String status) {
                    context.progress(status);
                }

                @Override
                public void progress(long progress, long total, String units, String status) {
                    context.progress(progress, total, units, status);
                }
            };
        }
    }

    public void reset() {
        log.records.clear();
    }
}
