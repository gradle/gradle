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
import groovy.lang.MissingPropertyException;
import groovy.lang.Tuple;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.ClassInfo;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.intercept.CallInterceptorResolver;
import org.gradle.internal.instrumentation.api.groovybytecode.CallInterceptor;
import org.gradle.internal.instrumentation.api.groovybytecode.InterceptScope;
import org.gradle.internal.instrumentation.api.groovybytecode.Invocation;
import org.gradle.internal.instrumentation.api.groovybytecode.InvocationImpl;
import org.gradle.internal.instrumentation.api.groovybytecode.PropertyAwareCallInterceptor;
import org.gradle.internal.instrumentation.api.groovybytecode.SignatureAwareCallInterceptor;
import org.gradle.internal.metaobject.InstrumentedMetaClass;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.GET_PROPERTY;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.SET_PROPERTY;

/**
 * A metaclass implementation that consults the Groovy call interception infrastructure ({@link InstrumentedGroovyCallsTracker} and {@link CallInterceptorResolver}).
 * It can both intercept calls to {@link MetaClass#invokeMethod} and provide a {@link MetaMethod} implementation in {@link MetaClass#pickMethod} that intercepts the calls.
 *
 * An important requirement for this implementation is to be able to intercept calls to declarations that are (no longer) present in the real class.
 *
 * It implements {@link AdaptingMetaClass} in order to tell the Groovy runtime that it cannot cache the metamethod instances, as it does with the default {@link MetaClassImpl}.
 */
@NullMarked
public class CallInterceptingMetaClass extends MetaClassImpl implements AdaptingMetaClass, InstrumentedMetaClass {

    private MetaClass adaptee;
    private final InstrumentedGroovyCallsTracker callsTracker;
    private final CallInterceptorResolver interceptorResolver;

    private static final Object[] NO_ARG = new Object[0];

    public CallInterceptingMetaClass(
        MetaClassRegistry registry,
        Class<?> javaClass,
        MetaClass adaptee,
        InstrumentedGroovyCallsTracker callsTracker,
        CallInterceptorResolver interceptorResolver
    ) {
        super(registry, javaClass);
        this.adaptee = adaptee;
        this.callsTracker = callsTracker;
        this.interceptorResolver = interceptorResolver;

        super.initialize();
    }

    @Override
    @Nullable
    public Object getProperty(Class sender, Object object, String name, boolean useSuper, boolean fromInsideClass) {
        if (useSuper || fromInsideClass) {
            return adaptee.getProperty(sender, object, name, useSuper, fromInsideClass);
        } else {
            return invokeIntercepted(object, GET_PROPERTY, name, NO_ARG, () -> adaptee.getProperty(sender, object, name, false, false));
        }
    }

    @Override
    @Nullable
    public Object getProperty(Object object, String property) {
        return invokeIntercepted(object, GET_PROPERTY, property, NO_ARG, () -> adaptee.getProperty(object, property));
    }

    @Override
    public void setProperty(Class sender, Object object, String name, Object newValue, boolean useSuper, boolean fromInsideClass) {
        if (useSuper || fromInsideClass) {
            adaptee.setProperty(sender, object, name, newValue, useSuper, fromInsideClass);
        } else {
            invokeIntercepted(object, SET_PROPERTY, name, new Object[]{newValue}, () -> {
                adaptee.setProperty(sender, object, name, newValue, useSuper, fromInsideClass);
                return null;
            });
        }
    }

    @Override
    public void setProperty(Object object, String property, Object newValue) {
        invokeIntercepted(object, SET_PROPERTY, property, new Object[]{newValue}, () -> {
            adaptee.setProperty(object, property, newValue);
            return null;
        });
    }

    @Override
    @Nullable
    public MetaProperty getMetaProperty(String name) {
        MetaProperty original = adaptee.getMetaProperty(name);
        Pair<String, CallInterceptor> getterCallerAndInterceptor = findGetterCallerAndInterceptor(name);
        Pair<String, CallInterceptor> setterCallerAndInterceptor = getterCallerAndInterceptor != null ? null : findSetterCallerAndInterceptor(name);
        if (getterCallerAndInterceptor != null || setterCallerAndInterceptor != null) {
            // TODO: the interceptor should tell the type
            String consumerClass = getterCallerAndInterceptor != null ? getterCallerAndInterceptor.left : setterCallerAndInterceptor.left;
            Class<?> propertyType = interceptedPropertyType(
                original,
                Optional.ofNullable(getterCallerAndInterceptor).map(Pair::right).orElse(null),
                Optional.ofNullable(setterCallerAndInterceptor).map(Pair::right).orElse(null)
            );
            if (propertyType != null) {
                return new InterceptedMetaProperty(name,
                    propertyType, original,
                    theClass, callsTracker, getterCallerAndInterceptor != null ? getterCallerAndInterceptor.right : null,
                    setterCallerAndInterceptor != null ? setterCallerAndInterceptor.right : null,
                    Objects.requireNonNull(consumerClass));
            }
        }
        return original;
    }

    private @Nullable Class<?> interceptedPropertyType(
        @Nullable MetaProperty originalProperty,
        @Nullable CallInterceptor getterInterceptor,
        @Nullable CallInterceptor setterInterceptor
    ) {
        if (getterInterceptor instanceof PropertyAwareCallInterceptor) {
            Class<?> typeFromGetter = ((PropertyAwareCallInterceptor) getterInterceptor).matchesProperty(theClass);
            if (typeFromGetter != null) {
                return typeFromGetter;
            }
        }
        if (setterInterceptor instanceof PropertyAwareCallInterceptor) {
            Class<?> typeFromSetter = ((PropertyAwareCallInterceptor) setterInterceptor).matchesProperty(theClass);
            if (typeFromSetter != null) {
                return typeFromSetter;
            }
        }
        if (originalProperty != null) {
            return originalProperty.getType();
        }
        return null;
    }

    @Override
    @Nullable
    public Object invokeMethod(Object object, String methodName, @Nullable Object arguments) {
        Object[] argsForInterceptor = arguments == null ? MetaClassHelper.EMPTY_ARRAY :
            arguments instanceof Tuple ? ((Tuple<?>) arguments).toArray() :
                arguments instanceof Object[] ? (Object[]) arguments :
                    new Object[]{arguments};

        return invokeIntercepted(object, INVOKE_METHOD, methodName, argsForInterceptor, () -> adaptee.invokeMethod(object, methodName, arguments));
    }

    @Override
    @Nullable
    public Object invokeMethod(Object object, String methodName, Object[] arguments) {
        return invokeIntercepted(object, INVOKE_METHOD, methodName, arguments, () -> adaptee.invokeMethod(object, methodName, arguments));
    }

    @Override
    @Nullable
    public Object invokeMethod(Class sender, Object object, String methodName, Object[] originalArguments, boolean isCallToSuper, boolean fromInsideClass) {
        if (isCallToSuper || fromInsideClass) {
            // Calls to super are not supported by the call interception mechanisms as of now
            return adaptee.invokeMethod(sender, object, methodName, originalArguments, isCallToSuper, fromInsideClass);
        }
        return invokeIntercepted(object, INVOKE_METHOD, methodName, originalArguments, () -> adaptee.invokeMethod(sender, object, methodName, originalArguments, isCallToSuper, fromInsideClass));
    }

    @Override
    @Nullable
    public MetaMethod pickMethod(String methodName, Class[] arguments) {
        String matchedCaller = callsTracker.findCallerForCurrentCallIfNotIntercepted(methodName, INVOKE_METHOD);
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
                        signatureMatch.isVararg,
                        callsTracker);
                }
            }
        }

        return original;
    }

    @Nullable
    private Object invokeIntercepted(Object receiver, InstrumentedGroovyCallsTracker.CallKind kind, String name, Object[] arguments, Callable<Object> invokeOriginal) {
        String matchedCaller = callsTracker.findCallerForCurrentCallIfNotIntercepted(name, kind);
        if (matchedCaller != null) {
            InterceptScope scope =
                kind == INVOKE_METHOD ? InterceptScope.methodsNamed(name) :
                    kind == GET_PROPERTY ? InterceptScope.readsOfPropertiesNamed(name) :
                        kind == SET_PROPERTY ? InterceptScope.writesOfPropertiesNamed(name) :
                            null;
            if (scope == null) {
                throw new IllegalArgumentException("unexpected invocation with kind " + kind);
            }
            CallInterceptor callInterceptor = interceptorResolver.resolveCallInterceptor(scope);
            if (callInterceptor != null) {
                return invokeWithInterceptor(callsTracker, callInterceptor, name, kind, receiver, arguments, matchedCaller, invokeOriginal);
            }
        }

        try {
            return invokeOriginal.call();
        } catch (Throwable e) {
            ThrowAsUnchecked.doThrow(e);
            throw new IllegalStateException("this is unreachable code, the call above should always throw an exception");
        }
    }

    @Override
    public synchronized void initialize() {
        this.adaptee.initialize();
        // Adaptee can override our metaclass, restore the entry to us.
        registry.setMetaClass(theClass, this);
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
    public Object invokeMissingMethod(Object instance, String methodName, Object[] arguments) {
        return adaptee.invokeMissingMethod(instance, methodName, arguments);
    }

    @Override
    public Object invokeMissingProperty(Object instance, String propertyName, Object optionalValue, boolean isGetter) {
        return adaptee.invokeMissingProperty(instance, propertyName, optionalValue, isGetter);
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
    public MetaMethod getStaticMetaMethod(String name, Object[] argTypes) {
        return adaptee.getStaticMetaMethod(name, argTypes);
    }

    /**
     * This is overridden just to make the compiler happy about the unchecked conversions.
     */
    @Override
    public List<MetaMethod> respondsTo(Object obj, String name, Object[] argTypes) {
        return Cast.uncheckedNonnullCast(super.respondsTo(obj, name, argTypes));
    }

    /**
     * This is overridden just to make the compiler happy about the unchecked conversions.
     */
    @Override
    public List<MetaMethod> respondsTo(Object obj, String name) {
        return Cast.uncheckedNonnullCast(super.respondsTo(obj, name));
    }
    //endregion

    @Nullable
    private static Object invokeWithInterceptor(InstrumentedGroovyCallsTracker callsTracker, CallInterceptor interceptor, String name, InstrumentedGroovyCallsTracker.CallKind kind, Object receiver, Object[] arguments, String consumerClass, Callable<Object> doCallOriginal) {
        final InvokedFlag invokedOriginal = new InvokedFlag();

        try {
            if (consumerClass.equals(callsTracker.findCallerForCurrentCallIfNotIntercepted(name, kind))) {
                Invocation invocation = callOriginalReportingInvocation(receiver, arguments, doCallOriginal, invokedOriginal);
                return interceptor.intercept(invocation, consumerClass);
            } else {
                invokedOriginal.run();
                return doCallOriginal.call();
            }
        } catch (Throwable throwable) {
            ThrowAsUnchecked.doThrow(throwable);
            throw new IllegalStateException("this is an unreachable statement, the call above always throws an exception");
        } finally {
            if (!invokedOriginal.invoked) {
                callsTracker.markCurrentCallAsIntercepted(name, kind);
            }
        }
    }

    private static Invocation callOriginalReportingInvocation(Object receiver, Object[] arguments, Callable<Object> doCallOriginal, Runnable reportCallOriginal) {
        return new InvocationImpl<>(receiver, arguments, () -> {
            reportCallOriginal.run();
            return doCallOriginal.call();
        });
    }

    @NullMarked
    static class InvokedFlag implements Runnable {
        public boolean invoked = false;

        @Override
        public void run() {
            invoked = true;
        }
    }

    @Override
    public boolean interceptsPropertyAccess(String propertyName) {
        return findGetterCallerAndInterceptor(propertyName) != null || findSetterCallerAndInterceptor(propertyName) != null;
    }

    @Nullable
    private Pair<String, CallInterceptor> findGetterCallerAndInterceptor(String propertyName) {
        String caller = callsTracker.findCallerForCurrentCallIfNotIntercepted(propertyName, GET_PROPERTY);
        if (caller == null) {
            return null;
        }
        CallInterceptor interceptor = interceptorResolver.resolveCallInterceptor(InterceptScope.readsOfPropertiesNamed(propertyName));
        if (interceptor == null) {
            return null;
        }
        return Pair.of(caller, interceptor);
    }

    @Nullable
    private Pair<String, CallInterceptor> findSetterCallerAndInterceptor(String propertyName) {
        String caller =  callsTracker.findCallerForCurrentCallIfNotIntercepted(propertyName, SET_PROPERTY);
        if (caller == null) {
            return null;
        }
        CallInterceptor interceptor = interceptorResolver.resolveCallInterceptor(InterceptScope.writesOfPropertiesNamed(propertyName));
        if (interceptor == null) {
            return null;
        }
        return Pair.of(caller, interceptor);
    }

    @NullMarked
    public static class InterceptedMetaProperty extends MetaProperty {
        @Nullable
        private final MetaProperty original;
        private final Class<?> ownerClass;
        private final InstrumentedGroovyCallsTracker callsTracker;
        private final CallInterceptor getterInterceptor;
        private final CallInterceptor setterInterceptor;
        private final String consumerClass;

        public InterceptedMetaProperty(
            String name,
            Class type,
            @Nullable MetaProperty original,
            Class<?> ownerClass, InstrumentedGroovyCallsTracker callsTracker, @Nullable CallInterceptor getterInterceptor,
            @Nullable CallInterceptor setterInterceptor,
            String getConsumerClass
        ) {
            super(name, type);
            this.original = original;
            this.ownerClass = ownerClass;
            this.callsTracker = callsTracker;
            this.getterInterceptor = getterInterceptor;
            this.setterInterceptor = setterInterceptor;
            this.consumerClass = getConsumerClass;
        }

        @Override
        @Nullable
        public Object getProperty(Object object) {
            if (getterInterceptor != null) {
                return invokeWithInterceptor(callsTracker, getterInterceptor, name, GET_PROPERTY, object, NO_ARG, consumerClass, () -> {
                    if (original != null) {
                        return original.getProperty(object);
                    } else {
                        throw new MissingPropertyException(name);
                    }
                });
            }
            if (original != null) {
                return original.getProperty(object);
            }
            throw new MissingPropertyException(name, ownerClass);
        }

        @Override
        public void setProperty(Object object, Object newValue) {
            if (setterInterceptor != null) {
                invokeWithInterceptor(callsTracker, setterInterceptor, name, SET_PROPERTY, object, new Object[]{newValue}, consumerClass, () -> {
                    if (original != null) {
                        original.setProperty(object, newValue);
                        return null;
                    } else {
                        throw new MissingPropertyException(name);
                    }
                });
            } else if (original != null) {
                original.setProperty(object, newValue);
            } else {
                throw new MissingPropertyException(name, ownerClass);
            }
        }
    }

    @NullMarked
    public static class InterceptedMetaMethod extends MetaMethod {
        private final @Nullable MetaMethod original;
        private final InstrumentedGroovyCallsTracker callsTracker;
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
            boolean isVararg,
            InstrumentedGroovyCallsTracker callsTracker
        ) {
            this.original = original;
            this.name = name;
            this.owner = owner;
            this.consumerClass = consumerClass;
            this.callInterceptor = callInterceptor;
            this.callsTracker = callsTracker;
            this.nativeParamTypes = nativeParameterTypes;
            this.isVargsMethod = isVararg;
        }

        @Override
        @Nullable
        public Object invoke(Object object, Object[] arguments) {
            return invokeWithInterceptor(callsTracker, callInterceptor, name, INVOKE_METHOD, object, arguments, consumerClass, () -> {
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

@NullMarked
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
