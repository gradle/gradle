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
import java.util.Deque;
import java.util.LinkedList;
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

    private final ApplicationStack applicationStack = new ApplicationStack();

    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultListenerBuildOperationDecorator(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public <T> Action<T> decorate(Action<T> action) {
        if (action instanceof InternalListener) {
            return action;
        }
        return new BuildOperationEmittingAction<T>(applicationStack.currentApplication(), action);
    }

    public <T> Closure<T> decorate(Closure<T> closure) {
        return new BuildOperationEmittingClosure<T>(applicationStack.currentApplication(), closure);
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
            BuildOperationEmittingInvocationHandler handler = new BuildOperationEmittingInvocationHandler(applicationStack.currentApplication(), listener);
            return targetClass.cast(Proxy.newProxyInstance(listenerClass.getClassLoader(), allInterfaces.toArray(new Class[0]), handler));
        }
        return listener;
    }

    public Object decorateUnknownListener(Object listener) {
        return decorate(Object.class, listener);
    }

    private static boolean isSupported(Object listener) {
        for(Class<?> i : SUPPORTED_INTERFACES) {
            if (i.isInstance(listener)) {
                return true;
            }
        }
        return false;
    }

    private abstract class BuildOperationEmitter {

        protected final ApplicationStackEntry application;

        BuildOperationEmitter(ApplicationStackEntry application) {
            this.application = application;
        }

        protected abstract class Operation implements RunnableBuildOperation {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return new DetailsImpl(application == null ? null : application.parentApplicationId()).desc();
            }
        }

    }

    private class BuildOperationEmittingAction<T> extends BuildOperationEmitter implements Action<T> {

        private final Action<T> delegate;

        private BuildOperationEmittingAction(ApplicationStackEntry application, Action<T> delegate) {
            super(application);
            this.delegate = delegate;
        }

        @Override
        public void execute(final T arg) {
            buildOperationExecutor.run(new Operation() {
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

        private final ApplicationStackEntry application;
        private final Closure<T> delegate;

        private BuildOperationEmittingClosure(ApplicationStackEntry application, Closure<T> delegate) {
            super(delegate.getOwner(), delegate.getThisObject());
            this.application = application;
            this.delegate = delegate;
        }

        public void doCall(final T arg) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    long executionId = applicationStack.startListenerExecution(application);
                    try {
                        delegate.call(arg);
                        context.setResult(RESULT);
                    } finally {
                        applicationStack.finishListenerExecution(executionId);
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return new DetailsImpl(application == null ? null : application.parentApplicationId()).desc();
                }
            });
        }

        @Override
        public void setDelegate(Object delegateObject) {
            delegate.setDelegate(delegateObject);
        }
    }

    private class BuildOperationEmittingInvocationHandler extends BuildOperationEmitter implements InvocationHandler {

        private final Object delegate;

        protected BuildOperationEmittingInvocationHandler(ApplicationStackEntry application, Object delegate) {
            super(application);
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
            final String methodName = method.getName();
            if (methodName.equals("toString") && args.length == 0) {
                return "BuildOperationEmittingBuildListenerInvocationHandler{delegate: " + delegate + "}";
            } else if (methodName.equals("hashCode") && args == null || args.length == 0) {
                return delegate.hashCode();
            } else if (methodName.equals("equals") && args.length == 1) {
                return proxy == args[0] || delegate.equals(args[0]) || args[0] instanceof BuildOperationEmittingInvocationHandler && delegate.equals(((BuildOperationEmittingInvocationHandler) args[0]).delegate);
            } else if(!SUPPORTED_INTERFACES.contains(method.getDeclaringClass()) || UNDECORATED_METHOD_NAMES.contains(methodName)){
                // just execute directly
                return method.invoke(delegate, args);
            } else {
                buildOperationExecutor.run(new Operation() {
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

    private static class ApplicationStack extends ThreadLocal<Deque<ApplicationStackEntry>> {
        private final AtomicLong applicationIdSequence = new AtomicLong();

        @Override
        protected Deque<ApplicationStackEntry> initialValue() {
            return new LinkedList<ApplicationStackEntry>();
        }

        public long allocateApplicationId() {
            return applicationIdSequence.incrementAndGet();
        }

        private void startApplication(long id) {
            pushEntry(ApplicationStackEntryType.Application, id, null);
        }

        private void finishApplication(long id) {
            popEntry(ApplicationStackEntryType.Application, id);
        }

        private long startListenerExecution(ApplicationStackEntry parent) {
            long id = allocateApplicationId();
            // this is to support attributing nested listener execution (primarily afterEvaluate at this time)
//            ApplicationStackEntry currentApplication = currentApplication();
//            System.out.println("startListenerExecution(): id: " + id + ", parent application: " + parent);
            pushEntry(ApplicationStackEntryType.ListenerExecution, id, parent == null ? null : parent.parentApplicationId());
            return id;
        }

        private void finishListenerExecution(long id) {
            popEntry(ApplicationStackEntryType.ListenerExecution, id);
        }

        private ApplicationStackEntry currentApplication() {
            return get().peek();
        }

        private void pushEntry(ApplicationStackEntryType type, Long id, Long parentApplicationId) {
//            System.out.println("Pushing " + new ApplicationStackEntry(type, id, parentApplicationId));
            get().push(new ApplicationStackEntry(type, id, parentApplicationId));
        }

        private void popEntry(ApplicationStackEntryType type, Long id) {
            ApplicationStackEntry expected = new ApplicationStackEntry(type, id, null);
            ApplicationStackEntry head = get().pop();
//            System.out.println("Popped " + head);
            if (!head.equals(expected)) {
                throw new IllegalStateException("Mismatching application stack, ending " + expected + " but stack had " + head);
            }
        }

    }

    private enum ApplicationStackEntryType {
        Application, ListenerExecution
    }

    private static class ApplicationStackEntry {
        private final ApplicationStackEntryType type;
        private final long applicationId;
        private final Long parentApplicationId;

        private ApplicationStackEntry(ApplicationStackEntryType type, long applicationId, Long parentApplicationId) {
            this.type = type;
            this.applicationId = applicationId;
            this.parentApplicationId = parentApplicationId;
        }

        private long parentApplicationId() {
            return parentApplicationId != null ? parentApplicationId : applicationId;
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

            ApplicationStackEntry that = (ApplicationStackEntry) o;

            if (type != that.type) {
                return false;
            }
            return applicationId == that.applicationId;
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + (int) (applicationId ^ (applicationId >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "ApplicationStackEntry{"
                + "type=" + type
                + ", applicationId=" + applicationId
                + ", parentApplicationId=" + parentApplicationId
                + '}';
        }
    }
}
