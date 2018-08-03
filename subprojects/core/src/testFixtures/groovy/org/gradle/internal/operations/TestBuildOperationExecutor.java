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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> generator) {
        generator.execute(new TestBuildOperationQueue<O>(log));
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        throw new UnsupportedOperationException();
    }


    private static class TestBuildOperationContext implements BuildOperationContext {

        private Object result;
        private String status;
        private Throwable failure;

        @Override
        public void failed(@Nullable Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void setResult(@Nullable Object result) {
            this.result = result;
        }

        @Override
        public void setStatus(String status) {
            this.status = status;
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
        public final List<Record> records = Collections.synchronizedList(new ArrayList<Record>());

        public List<BuildOperationDescriptor> getDescriptors() {
            return Lists.transform(new ArrayList<Record>(records), new Function<Record, BuildOperationDescriptor>() {
                @Override
                public BuildOperationDescriptor apply(@javax.annotation.Nullable Record input) {
                    return input.descriptor;
                }
            });
        }

        private <D, R, T extends BuildOperationType<D, R>> Record mostRecent(Class<T> type) {
            Class<D> detailsType = BuildOperationTypes.detailsType(type);
            ImmutableList<Record> copy = ImmutableList.copyOf(this.records).reverse();
            for (Record record : copy) {
                Object details = record.descriptor.getDetails();
                if (detailsType.isInstance(details)) {
                    return record;
                }
            }

            throw new AssertionError("Did not find operation with details of type: " + detailsType.getName());
        }

        public <D, R, T extends BuildOperationType<D, R>> List<Record> all(Class<T> type) {
            final Class<D> detailsType = BuildOperationTypes.detailsType(type);
            return ImmutableList.copyOf(Iterables.filter(records, new Predicate<Record>() {
                @Override
                public boolean apply(Record input) {
                    return detailsType.isInstance(input.descriptor.getDetails());
                }
            }));
        }

        public <R, D, T extends BuildOperationType<D, R>> D mostRecentDetails(Class<T> type) {
            Class<D> detailsType = BuildOperationTypes.detailsType(type);
            return detailsType.cast(mostRecent(type).descriptor.getDetails());
        }

        public <R, D, T extends BuildOperationType<D, R>> R mostRecentResult(Class<T> type) {
            Record record = mostRecent(type);
            Object result = record.result;
            Class<R> resultType = BuildOperationTypes.resultType(type);
            if (resultType.isInstance(result)) {
                return resultType.cast(result);
            } else {
                throw new AssertionError("Expected result type " + resultType.getName() + ", got " + result.getClass().getName());
            }
        }

        public <D, R, T extends BuildOperationType<D, R>> Throwable mostRecentFailure(Class<T> type) {
            return mostRecent(type).failure;
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
        }

        private void run(RunnableBuildOperation buildOperation) {
            Record record = new Record(buildOperation.description().build());
            records.add(record);
            TestBuildOperationContext context = new TestBuildOperationContext();
            try {
                buildOperation.run(context);
            } catch (Throwable failure) {
                if (record.result == null) {
                    record.result = context.result;
                }
                if (record.failure == null) {
                    record.failure = failure;
                }
                throw UncheckedException.throwAsUncheckedException(failure);
            }
            record.result = context.result;
            if (context.failure != null) {
                record.failure = context.failure;
            }
        }

        private <T> T call(CallableBuildOperation<T> buildOperation) {
            Record record = new Record(buildOperation.description().build());
            records.add(record);
            TestBuildOperationContext context = new TestBuildOperationContext();
            T t;
            try {
                t = buildOperation.call(context);
            } catch (Throwable failure) {
                if (record.failure == null) {
                    record.failure = failure;
                }
                throw UncheckedException.throwAsUncheckedException(failure);
            }
            record.result = context.result;
            return t;
        }
    }

    public void reset() {
        log.records.clear();
    }
}
