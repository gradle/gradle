/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import groovy.lang.AdaptingMetaClass;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassImpl;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import groovy.lang.MissingMethodException;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.ClassInfo;
import org.gradle.api.NonNullApi;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.intercept.AbstractInvocation;
import org.gradle.internal.classpath.intercept.CallInterceptor;
import org.gradle.internal.classpath.intercept.CallInterceptorResolver;
import org.gradle.internal.classpath.intercept.InterceptScope;
import org.gradle.internal.classpath.intercept.Invocation;
import org.gradle.internal.classpath.intercept.SignatureAwareCallInterceptor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD;

/**
 * A metaclass implementation that consults the Groovy call interception infrastructure ({@link InstrumentedGroovyCallsTracker} and {@link CallInterceptorResolver}).
 * It can both intercept calls to {@link MetaClass#invokeMethod} and provide a {@link MetaMethod} implementation in {@link MetaClass#pickMethod} that intercepts the calls.
 *
 * An important requirement for this implementation is to be able to intercept calls to declarations that are (no longer) present in the real class.
 *
 * It implements {@link AdaptingMetaClass} in order to tell the Groovy runtime that it cannot cache the metamethod instances, as it does with the default {@link MetaClassImpl}.
 */
@NonNullApi
public class CallInterceptingMetaClass extends MetaClassImpl implements AdaptingMetaClass {

    private MetaClass adaptee;
    private final CallInterceptorResolver interceptorResolver;

    public CallInterceptingMetaClass(
        MetaClassRegistry registry,
        Class<?> javaClass,
        MetaClass adaptee,

        CallInterceptorResolver interceptorResolver
    ) {
        super(registry, javaClass);
        this.adaptee = adaptee;
        this.interceptorResolver = interceptorResolver;

        super.initialize();
    }

    @Override
    public Object invokeMethod(Object object, String methodName, Object[] arguments) {
        return invokeIntercepted(object, methodName, arguments, () -> adaptee.invokeMethod(object, methodName, arguments));
    }

    @Override
    public Object invokeMethod(Class sender, Object object, String methodName, Object[] originalArguments, boolean isCallToSuper, boolean fromInsideClass) {
        if (isCallToSuper) {
            // Calls to super are not supported by the call interception mechanisms as of now
            return adaptee.invokeMethod(sender, object, methodName, originalArguments, true, fromInsideClass);
        }
        return invokeIntercepted(object, methodName, originalArguments, () -> adaptee.invokeMethod(sender, object, methodName, originalArguments, false, fromInsideClass));
    }

    @Override
    public MetaMethod pickMethod(String methodName, Class[] arguments) {
        String matchedCaller = InstrumentedGroovyCallsTracker.findCallerForCurrentCallIfNotIntercepted(methodName, INVOKE_METHOD);
        MetaMethod original = adaptee.pickMethod(methodName, arguments);

        if (matchedCaller != null) {
            CallInterceptor callInterceptor = interceptorResolver.resolveCallInterceptor(InterceptScope.methodsNamed(methodName));
            if (callInterceptor instanceof SignatureAwareCallInterceptor) {
                SignatureAwareCallInterceptor.SignatureMatch signatureMatch = ((SignatureAwareCallInterceptor) callInterceptor).matchesMethodSignature(theClass, arguments, false);
                if (signatureMatch != null) {
                    return new InterceptedMetaMethod(
                        original,
                        methodName,
                        theClass,
                        matchedCaller,
                        callInterceptor,
                        signatureMatch.argClasses,
                        signatureMatch.isVararg
                    );
                }
            }
        }

        return original;
    }

    private Object invokeIntercepted(Object receiver, String methodName, Object[] arguments, Callable<Object> invokeOriginal) {
        String matchedCaller = InstrumentedGroovyCallsTracker.findCallerForCurrentCallIfNotIntercepted(methodName, INVOKE_METHOD);
        if (matchedCaller != null) {
            CallInterceptor callInterceptor = interceptorResolver.resolveCallInterceptor(InterceptScope.methodsNamed(methodName));
            if (callInterceptor != null) {
                return invokeWithInterceptor(callInterceptor, methodName, INVOKE_METHOD, receiver, arguments, matchedCaller, invokeOriginal);
            }
        }

        try {
            return invokeOriginal.call();
        } catch (Throwable e) {
            ThrowAsUnchecked.doThrow(e);
            throw new IllegalStateException("this is unreachable code, the call above should always throw an exception");
        }
    }

    //region implementations delegating to adaptee
    @Override
    public MetaClass getAdaptee() {
        return adaptee;
    }

    @Override
    public void setAdaptee(MetaClass metaClass) {
        adaptee = metaClass;
    }

    @Override
    public synchronized void initialize() {
        this.adaptee.initialize();
    }

    @Override
    public Object invokeMissingMethod(Object instance, String methodName, Object[] arguments) {
        return adaptee.invokeMissingMethod(instance, methodName, arguments);
    }

    @Override
    public Object invokeMissingProperty(Object instance, String propertyName, Object optionalValue, boolean isGetter) {
        return adaptee.invokeMissingProperty(instance, propertyName, optionalValue, isGetter);
    }

    @Override
    public Object getProperty(Object object, String property) {
        return adaptee.getProperty(object, property);
    }

    @Override
    public void setProperty(Object object, String property, Object newValue) {
        adaptee.setProperty(object, property, newValue);
    }

    @Override
    public Object invokeConstructor(Object[] arguments) {
        return adaptee.invokeConstructor(arguments);
    }

    @Override
    public Object invokeStaticMethod(Object object, String methodName, Object[] arguments) {
        return adaptee.invokeStaticMethod(object, methodName, arguments);
    }

    @Override
    public MetaProperty getMetaProperty(String name) {
        return adaptee.getMetaProperty(name);
    }

    @Override
    public MetaMethod getStaticMetaMethod(String name, Object[] argTypes) {
        return adaptee.getStaticMetaMethod(name, argTypes);
    }

    /**
     * This is overridden just to make the compiler happy about the unchecked conversions.
     */
    @Override
    public List<MetaMethod> respondsTo(Object obj, String name, Object[] argTypes) {
        return Cast.uncheckedCast(super.respondsTo(obj, name, argTypes));
    }

    /**
     * This is overridden just to make the compiler happy about the unchecked conversions.
     */
    @Override
    public List<MetaMethod> respondsTo(Object obj, String name) {
        return Cast.uncheckedCast(super.respondsTo(obj, name));
    }
    //endregion

    private static Object invokeWithInterceptor(CallInterceptor interceptor, String name, InstrumentedGroovyCallsTracker.CallKind kind, Object receiver, Object[] arguments, String consumerClass, Callable<Object> doCallOriginal) {
        final boolean[] invokedOriginal = {false};

        Invocation invocation = callOriginalReportingInvocation(receiver, arguments, doCallOriginal, () -> invokedOriginal[0] = true);

        try {
            return interceptor.doIntercept(invocation, consumerClass);
        } catch (Throwable throwable) {
            ThrowAsUnchecked.doThrow(throwable);
            throw new IllegalStateException("this is an unreachable statement, the call above always throws an exception");
        } finally {
            if (!invokedOriginal[0]) {
                InstrumentedGroovyCallsTracker.markCurrentCallAsIntercepted(name, kind);
            }
        }
    }

    private static Invocation callOriginalReportingInvocation(Object receiver, Object[] arguments, Callable<Object> doCallOriginal, Runnable reportCallOriginal) {
        @NonNullApi
        class InvocationImpl extends AbstractInvocation<Object> {
            public InvocationImpl(Object receiver, Object[] args) {
                super(receiver, args);
            }

            @Override
            public Object callOriginal() throws Exception {
                reportCallOriginal.run();
                return doCallOriginal.call();
            }
        }
        return new InvocationImpl(receiver, arguments);
    }

    @NonNullApi
    public static class InterceptedMetaMethod extends MetaMethod {
        private final @Nullable MetaMethod original;
        private final String name;
        private final Class<?> owner;
        private final String consumerClass;
        private final CallInterceptor callInterceptor;

        public InterceptedMetaMethod(
            @Nullable MetaMethod original,
            String name,
            Class<?> owner,
            String consumerClass,
            CallInterceptor callInterceptor,
            Class<?>[] nativeParameterTypes,
            boolean isVararg
        ) {
            this.original = original;
            this.name = name;
            this.owner = owner;
            this.consumerClass = consumerClass;
            this.callInterceptor = callInterceptor;
            this.nativeParamTypes = nativeParameterTypes;
            this.isVargsMethod = isVararg;
        }

        @Override
        public Object invoke(Object object, Object[] arguments) {
            return invokeWithInterceptor(callInterceptor, name, INVOKE_METHOD, object, arguments, consumerClass, () -> {
                if (original != null) {
                    return original.invoke(object, arguments);
                } else {
                    throw new MissingMethodException(name, owner, arguments);
                }
            });
        }

        @Override
        public int getModifiers() {
            if (original != null) {
                return original.getModifiers();
            }
            // TODO is there even a way to return meaningful modifiers when we are intercepting calls to a missing method?
            return 0;
        }

        @Override
        public String getName() {
            if (original != null) {
                return original.getName();
            }
            return name;
        }

        @Override
        public Class<?>[] getNativeParameterTypes() {
            if (original != null) {
                return original.getNativeParameterTypes();
            }
            return super.getNativeParameterTypes();
        }

        @Override
        public Class<?> getReturnType() {
            if (original != null) {
                return original.getReturnType();
            }
            return Object.class;
        }

        @Override
        public CachedClass getDeclaringClass() {
            if (original != null) {
                return original.getDeclaringClass();
            }
            return new CachedClass(owner, ClassInfo.getClassInfo(owner));
        }
    }
}

@NonNullApi
class ThrowAsUnchecked {
    /**
     * Provides a way to throw an arbitrary {@link Throwable} as an unchecked exception, working around the Java compiler check for signature declaration
     */
    @SuppressWarnings("unchecked")
    static <T extends Throwable> void doThrow(Throwable e) throws T {
        //noinspection unchecked
        throw (T) e;
    }
}
