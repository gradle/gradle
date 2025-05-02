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
import org.gradle.internal.InternalListener;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeApplicationId;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
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
        "beforeSettings",
        "settingsEvaluated",
        "buildFinished"
    );

    private final BuildOperationRunner buildOperationRunner;
    private final UserCodeApplicationContext userCodeApplicationContext;

    public DefaultListenerBuildOperationDecorator(BuildOperationRunner buildOperationRunner, UserCodeApplicationContext userCodeApplicationContext) {
        this.buildOperationRunner = buildOperationRunner;
        this.userCodeApplicationContext = userCodeApplicationContext;
    }

    @Override
    public <T> Action<T> decorate(String registrationPoint, Action<T> action) {
        UserCodeApplicationContext.Application application = userCodeApplicationContext.current();
        if (application == null || action instanceof InternalListener) {
            return action;
        }
        Action<T> decorated = application.reapplyLater(action);
        return new BuildOperationEmittingAction<>(application.getId(), registrationPoint, decorated);
    }

    @Override
    public <T> Closure<T> decorate(String registrationPoint, Closure<T> closure) {
        UserCodeApplicationContext.Application application = userCodeApplicationContext.current();
        if (application == null) {
            return closure;
        }
        return new BuildOperationEmittingClosure<>(application, registrationPoint, closure);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decorate(String registrationPoint, Class<T> targetClass, T listener) {
        if (listener instanceof InternalListener || !isSupported(listener)) {
            return listener;
        }

        UserCodeApplicationContext.Application application = userCodeApplicationContext.current();
        if (application == null) {
            return listener;
        }

        Class<?> listenerClass = listener.getClass();
        List<Class<?>> allInterfaces = ClassUtils.getAllInterfaces(listenerClass);
        BuildOperationEmittingInvocationHandler handler = new BuildOperationEmittingInvocationHandler(application, registrationPoint, listener);
        return targetClass.cast(Proxy.newProxyInstance(listenerClass.getClassLoader(), allInterfaces.toArray(new Class[0]), handler));
    }

    @Override
    public Object decorateUnknownListener(String registrationPoint, Object listener) {
        return decorate(registrationPoint, Object.class, listener);
    }

    private static boolean isSupported(Object listener) {
        for (Class<?> i : SUPPORTED_INTERFACES) {
            if (i.isInstance(listener)) {
                return true;
            }
        }
        return false;
    }

    private static abstract class Operation implements RunnableBuildOperation {

        private final UserCodeApplicationId applicationId;
        private final String registrationPoint;

        Operation(UserCodeApplicationId applicationId, String registrationPoint) {
            this.applicationId = applicationId;
            this.registrationPoint = registrationPoint;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor
                .displayName("Execute " + registrationPoint + " listener")
                .details(new ExecuteListenerDetails(applicationId, registrationPoint));
        }
    }

    private static class ExecuteListenerDetails implements ExecuteListenerBuildOperationType.Details {
        final UserCodeApplicationId applicationId;
        final String registrationPoint;

        ExecuteListenerDetails(UserCodeApplicationId applicationId, String registrationPoint) {
            this.applicationId = applicationId;
            this.registrationPoint = registrationPoint;
        }

        @Override
        public long getApplicationId() {
            return applicationId.longValue();
        }

        @Override
        public String getRegistrationPoint() {
            return registrationPoint;
        }
    }

    private class BuildOperationEmittingAction<T> implements Action<T> {

        private final UserCodeApplicationId applicationId;
        private final String registrationPoint;
        private final Action<T> delegate;

        private BuildOperationEmittingAction(UserCodeApplicationId applicationId, String registrationPoint, Action<T> delegate) {
            this.applicationId = applicationId;
            this.delegate = delegate;
            this.registrationPoint = registrationPoint;
        }

        @Override
        public void execute(final T arg) {
            buildOperationRunner.run(new Operation(applicationId, registrationPoint) {
                @Override
                public void run(final BuildOperationContext context) {
                    delegate.execute(arg);
                    context.setResult(RESULT);
                }
            });
        }
    }

    private class BuildOperationEmittingClosure<T> extends Closure<T> {

        private final UserCodeApplicationContext.Application application;
        private final String registrationPoint;
        private final Closure<T> delegate;

        private BuildOperationEmittingClosure(UserCodeApplicationContext.Application application, String registrationPoint, Closure<T> delegate) {
            super(delegate.getOwner(), delegate.getThisObject());
            this.application = application;
            this.delegate = delegate;
            this.registrationPoint = registrationPoint;
        }

        @SuppressWarnings("unused")
        public void doCall(final Object... args) {
            buildOperationRunner.run(new Operation(application.getId(), registrationPoint) {
                @Override
                public void run(final BuildOperationContext context) {
                    application.reapply(() -> {
                        int numClosureArgs = delegate.getMaximumNumberOfParameters();
                        Object[] finalArgs = numClosureArgs < args.length ? Arrays.copyOf(args, numClosureArgs) : args;
                        delegate.call(finalArgs);
                        context.setResult(RESULT);
                    });
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

    private class BuildOperationEmittingInvocationHandler implements InvocationHandler {

        private final UserCodeApplicationContext.Application application;
        private final String registrationPoint;
        private final Object delegate;

        private BuildOperationEmittingInvocationHandler(UserCodeApplicationContext.Application application, String registrationPoint, Object delegate) {
            this.application = application;
            this.registrationPoint = registrationPoint;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
            final String methodName = method.getName();
            if (methodName.equals("toString") && (args == null || args.length == 0)) {
                return "BuildOperationEmittingBuildListenerInvocationHandler{delegate: " + delegate + "}";
            } else if (methodName.equals("hashCode") && (args == null || args.length == 0)) {
                return delegate.hashCode();
            } else if (methodName.equals("equals") && args.length == 1) {
                return proxy == args[0] || isSame(args[0]);
            } else if (!SUPPORTED_INTERFACES.contains(method.getDeclaringClass()) || UNDECORATED_METHOD_NAMES.contains(methodName)) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw UncheckedException.unwrapAndRethrow(e);
                }
            } else {
                buildOperationRunner.run(new Operation(application.getId(), registrationPoint) {
                    @Override
                    public void run(final BuildOperationContext context) {
                        application.reapply(() -> {
                            try {
                                method.invoke(delegate, args);
                                context.setResult(RESULT);
                            } catch (InvocationTargetException e) {
                                throw UncheckedException.unwrapAndRethrow(e);
                            } catch (Exception e) {
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                        });
                    }
                });

                // all of the interfaces that we decorate have 100% void methods
                //noinspection ConstantConditions
                return null;
            }
        }

        private boolean isSame(Object arg) {
            if (Proxy.isProxyClass(arg.getClass())) {
                InvocationHandler invocationHandler = Proxy.getInvocationHandler(arg);
                if (getClass() == invocationHandler.getClass()) {
                    BuildOperationEmittingInvocationHandler cast = (BuildOperationEmittingInvocationHandler) invocationHandler;
                    return cast.delegate.equals(delegate);
                }
            }
            return false;
        }
    }


}
