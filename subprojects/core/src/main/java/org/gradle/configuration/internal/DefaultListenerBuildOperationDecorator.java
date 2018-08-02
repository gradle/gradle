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
import java.util.concurrent.atomic.AtomicLong;

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

    private final ThreadLocal<ApplicationStack> applicationStack = new ThreadLocal<ApplicationStack>() {
        @Override
        protected ApplicationStack initialValue() {
            return new ApplicationStack();
        }
    };

    private ApplicationStack getApplicationStack() {
        return applicationStack.get();
    }

    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultListenerBuildOperationDecorator(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public <T> Action<T> decorate(String name, Action<T> action) {
        if (action instanceof InternalListener) {
            return action;
        }
        return new BuildOperationEmittingAction<T>(getApplicationStack().current(), name, action);
    }

    public <T> Closure<T> decorate(String name, Closure<T> closure) {
        return new BuildOperationEmittingClosure<T>(getApplicationStack().current(), name, closure);
    }

    public long allocateApplicationId() {
        return getApplicationStack().allocateId();
    }

    public void startApplication(long id) {
        getApplicationStack().start(id);
    }

    public void finishApplication(long id) {
        getApplicationStack().finish(id);
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
            BuildOperationEmittingInvocationHandler handler = new BuildOperationEmittingInvocationHandler(getApplicationStack().current(), listener);
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

    private static BuildOperationDescriptor.Builder opDescriptor(long applicationId, String name) {
        return BuildOperationDescriptor
            .displayName("Execute " + name + " listener")
            .details(new DetailsImpl(applicationId));
    }

    private abstract class BuildOperationEmitter {

        protected final long applicationId;

        BuildOperationEmitter(long applicationId) {
            this.applicationId = applicationId;
        }

        protected abstract class Operation implements RunnableBuildOperation {

            private final String name;

            protected Operation(String name) {
                this.name = name;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return opDescriptor(applicationId, name);
            }
        }
    }

    private class BuildOperationEmittingAction<T> extends DefaultListenerBuildOperationDecorator.BuildOperationEmitter implements Action<T> {

        private final String name;
        private final Action<T> delegate;

        private BuildOperationEmittingAction(long applicationId, String name, Action<T> delegate) {
            super(applicationId);
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public void execute(final T arg) {
            buildOperationExecutor.run(new Operation(name) {
                @Override
                public void run(BuildOperationContext context) {
                    getApplicationStack().start(applicationId);
                    try {
                        delegate.execute(arg);
                        context.setResult(RESULT);
                    } finally {
                        getApplicationStack().finish(applicationId);
                    }
                }
            });
        }
    }

    private class BuildOperationEmittingClosure<T> extends Closure<T> {

        private final long applicationId;
        private final String name;
        private final Closure<T> delegate;

        private BuildOperationEmittingClosure(long application, String name, Closure<T> delegate) {
            super(delegate.getOwner(), delegate.getThisObject());
            this.applicationId = application;
            this.delegate = delegate;
            this.name = name;
        }

        public void doCall(final Object... args) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    getApplicationStack().start(applicationId);
                    try {
                        int numClosureArgs = delegate.getMaximumNumberOfParameters();
                        Object[] finalArgs = numClosureArgs < args.length ? Arrays.copyOf(args, numClosureArgs) : args;
                        delegate.call(finalArgs);
                        context.setResult(RESULT);
                    } finally {
                        getApplicationStack().finish(applicationId);
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return opDescriptor(applicationId, name);
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

        private BuildOperationEmittingInvocationHandler(long applicationId, Object delegate) {
            super(applicationId);
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
                        getApplicationStack().start(applicationId);
                        try {
                            method.invoke(delegate, args);
                            context.setResult(RESULT);
                        } catch (Exception e) {
                            context.failed(e);
                        } finally {
                            getApplicationStack().finish(applicationId);
                        }
                    }
                });
                // all of the interfaces that we decorate have 100% void methods
                return null;
            }
        }
    }

    private static class ApplicationStack {

        private static final AtomicLong COUNTER = new AtomicLong();
        private final Deque<Long> stack = new ArrayDeque<Long>();

        private long allocateId() {
            return COUNTER.incrementAndGet();
        }

        private void start(long id) {
            stack.push(id);
        }

        private void finish(long id) {
            long popped = stack.pop();
            if (popped != id) {
                throw new IllegalStateException("Mismatching application stack, ending " + id + " but stack had " + popped);
            }
        }

        private Long current() {
            return stack.peek();
        }

    }

}
