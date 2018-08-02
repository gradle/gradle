/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.configuration.internal;

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import org.apache.commons.lang.ClassUtils;
import org.gradle.BuildListener;
import org.gradle.api.Action;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.configuration.internal.ExecuteListenerBuildOperationType.DetailsImpl;
import org.gradle.internal.InternalListener;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.gradle.configuration.internal.ExecuteListenerBuildOperationType.RESULT;

public class DefaultListenerBuildOperationDecorator implements ListenerBuildOperationDecorator {

    private static final ImmutableSet<Class<?>> SUPPORTED_INTERFACES = ImmutableSet.of(
        BuildListener.class,
        ProjectEvaluationListener.class,
        TaskExecutionGraphListener.class
    );

    // we don't decorate everything in BuildListener, just projectsLoaded/projectsEvaluated
    private static final ImmutableSet<String> UNDECORATED_METHOD_NAMES = ImmutableSet.of(
        "buildStarted",
        "settingsEvaluated",
        "buildFinished"
    );

    private final ThreadApplicationStack applicationStack = new ThreadApplicationStack();

    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultListenerBuildOperationDecorator(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public <T> Action<T> decorate(String name, Action<T> action) {
        if (action instanceof InternalListener) {
            return action;
        }
        return new BuildOperationEmittingAction<T>(applicationStack.currentApplication(), name, action);
    }

    public <T> Closure<T> decorate(String name, Closure<T> closure) {
        return new BuildOperationEmittingClosure<T>(applicationStack.currentApplication(), name, closure);
    }

    public long allocateApplicationId() {
        return applicationStack.allocateApplicationId();
    }

    public void startApplication(long id) {
        applicationStack.startApplication(id);
    }

    public void finishApplication(long id) {
        applicationStack.finishApplication(id);
    }

    @SuppressWarnings("unchecked")
    public <T> T decorate(Class<T> targetClass, T listener) {
        if (listener instanceof InternalListener) {
            return listener;
        }
        if (isSupported(listener)) {
            Class<?> listenerClass = listener.getClass();
            List<Class<?>> allInterfaces = ClassUtils.getAllInterfaces(listenerClass);
            allInterfaces.add(BuildOperationEmittingListenerProxy.class);
            BuildOperationEmittingInvocationHandler handler = new BuildOperationEmittingInvocationHandler(applicationStack.currentApplication(), listener);
            return targetClass.cast(Proxy.newProxyInstance(listenerClass.getClassLoader(), allInterfaces.toArray(new Class[0]), handler));
        }
        return listener;
    }

    public Object decorateUnknownListener(Object listener) {
        return decorate(Object.class, listener);
    }

    private static boolean isSupported(Object listener) {
        for (Class<?> i : SUPPORTED_INTERFACES) {
            if (i.isInstance(listener)) {
                return true;
            }
        }
        return false;
    }

    private static BuildOperationDescriptor.Builder opDescriptor(ThreadApplicationStack.Entry application, String name) {
        return BuildOperationDescriptor
            .displayName("Execute " + name + " listener")
            .details(new DetailsImpl(application == null ? null : application.parentApplicationId()));
    }

    private abstract class BuildOperationEmitter {

        protected final ThreadApplicationStack.Entry application;

        BuildOperationEmitter(ThreadApplicationStack.Entry application) {
            this.application = application;
        }

        protected abstract class Operation implements RunnableBuildOperation {

            private final String name;

            protected Operation(String name) {
                this.name = name;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return opDescriptor(application, name);
            }
        }
    }

    private class BuildOperationEmittingAction<T> extends DefaultListenerBuildOperationDecorator.BuildOperationEmitter implements Action<T> {

        private final String name;
        private final Action<T> delegate;

        private BuildOperationEmittingAction(ThreadApplicationStack.Entry application, String name, Action<T> delegate) {
            super(application);
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public void execute(final T arg) {
            buildOperationExecutor.run(new Operation(name) {
                @Override
                public void run(BuildOperationContext context) {
                    long executionId = applicationStack.startListenerExecution(application);
                    try {
                        delegate.execute(arg);
                        context.setResult(RESULT);
                    } finally {
                        applicationStack.finishListenerExecution(executionId);
                    }
                }
            });
        }
    }

    private class BuildOperationEmittingClosure<T> extends Closure<T> {

        private final ThreadApplicationStack.Entry application;
        private final String name;
        private final Closure<T> delegate;

        private BuildOperationEmittingClosure(ThreadApplicationStack.Entry application, String name, Closure<T> delegate) {
            super(delegate.getOwner(), delegate.getThisObject());
            this.application = application;
            this.delegate = delegate;
            this.name = name;
        }

        public void doCall(final Object... args) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    long executionId = applicationStack.startListenerExecution(application);
                    try {
                        int numClosureArgs = delegate.getMaximumNumberOfParameters();
                        Object[] finalArgs = numClosureArgs < args.length ? Arrays.copyOf(args, numClosureArgs) : args;
                        delegate.call(finalArgs);
                        context.setResult(RESULT);
                    } finally {
                        applicationStack.finishListenerExecution(executionId);
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return opDescriptor(application, name);
                }
            });
        }

        @Override
        public void setDelegate(Object delegateObject) {
            delegate.setDelegate(delegateObject);
        }

        @Override
        public void setResolveStrategy(int resolveStrategy) {
            delegate.setResolveStrategy(resolveStrategy);
        }

        @Override
        public int getMaximumNumberOfParameters() {
            return delegate.getMaximumNumberOfParameters();
        }
    }

    public interface BuildOperationEmittingListenerProxy {
        Object getDelegate();
    }

    private class BuildOperationEmittingInvocationHandler extends BuildOperationEmitter implements InvocationHandler {

        private final Object delegate;

        protected BuildOperationEmittingInvocationHandler(ThreadApplicationStack.Entry application, Object delegate) {
            super(application);
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
            final String methodName = method.getName();
            if (methodName.equals("toString") && (args == null || args.length == 0)) {
                return "BuildOperationEmittingBuildListenerInvocationHandler{delegate: " + delegate + "}";
            } else if (methodName.equals("hashCode") && (args == null || args.length == 0)) {
                return delegate.hashCode();
            } else if (methodName.equals("getDelegate") && (args == null || args.length == 0)) {
                return delegate;
            } else if (methodName.equals("equals") && args.length == 1) {
                return proxy == args[0] || (args[0] instanceof BuildOperationEmittingListenerProxy && delegate.equals(((BuildOperationEmittingListenerProxy) args[0]).getDelegate()));
            } else if (!SUPPORTED_INTERFACES.contains(method.getDeclaringClass()) || UNDECORATED_METHOD_NAMES.contains(methodName)) {
                // just execute directly
                return method.invoke(delegate, args);
            } else {
                buildOperationExecutor.run(new Operation(methodName) {
                    @Override
                    public void run(BuildOperationContext context) {
                        long executionId = applicationStack.startListenerExecution(application);
                        try {
                            method.invoke(delegate, args);
                            context.setResult(RESULT);
                        } catch (Exception e) {
                            context.failed(e);
                        } finally {
                            applicationStack.finishListenerExecution(executionId);
                        }
                    }
                });
                // all of the interfaces that we decorate have 100% void methods
                return null;
            }
        }
    }

    private static class ThreadApplicationStack extends ThreadLocal<Deque<ThreadApplicationStack.Entry>> {
        private long counter;

        @Override
        protected Deque<Entry> initialValue() {
            return new ArrayDeque<Entry>();
        }

        private long allocateApplicationId() {
            return ++counter;
        }

        private void startApplication(long id) {
            pushEntry(Entry.Type.Application, id, null);
        }

        private void finishApplication(long id) {
            popEntry(Entry.Type.Application, id);
        }

        private long startListenerExecution(Entry parent) {
            long id = allocateApplicationId();
            // this is to support attributing nested listener execution (primarily afterEvaluate at this time)
            pushEntry(Entry.Type.ListenerExecution, id, parent == null ? null : parent.parentApplicationId());
            return id;
        }

        private void finishListenerExecution(long id) {
            popEntry(Entry.Type.ListenerExecution, id);
        }

        private Entry currentApplication() {
            return get().peek();
        }

        private void pushEntry(Entry.Type type, Long id, Long parentApplicationId) {
            get().push(new Entry(type, id, parentApplicationId));
        }

        private void popEntry(Entry.Type type, Long id) {
            Entry expected = new Entry(type, id, null);
            Entry head = get().pop();
            if (!head.equals(expected)) {
                throw new IllegalStateException("Mismatching application stack, ending " + expected + " but stack had " + head);
            }
        }

        private static class Entry {

            private enum Type {
                Application, ListenerExecution
            }

            private final Type type;
            private final long id;
            private final Long parentId;

            private Entry(Type type, long id, Long parentId) {
                this.type = type;
                this.id = id;
                this.parentId = parentId;
            }

            private long parentApplicationId() {
                return parentId != null ? parentId : id;
            }

            // note equals and hashCode aren't interested in the parent, only this execution

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                Entry that = (Entry) o;

                if (type != that.type) {
                    return false;
                }
                return id == that.id;
            }

            @Override
            public int hashCode() {
                int result = type.hashCode();
                result = 31 * result + (int) (id ^ (id >>> 32));
                return result;
            }

            @Override
            public String toString() {
                return "ApplicationStackEntry{"
                    + "type=" + type
                    + ", id=" + id
                    + ", parentId=" + parentId
                    + '}';
            }
        }
    }

}
