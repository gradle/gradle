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

import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import org.gradle.internal.classpath.intercept.CallInterceptorResolver;
import org.gradle.internal.classpath.intercept.CallInterceptorResolver.ClosureCallInterceptorResolver;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter.ALL;

/**
 * Injects the logic for Groovy calls instrumentation into the Groovy metaclasses.
 */
@NullMarked
public class InstrumentedGroovyMetaClassHelper {
    /**
     * Should be invoked on an object that a Groovy Closure can dispatch the calls to. Injects the call interception logic into the metaclass of that object.
     * This is done for closure delegates that are reassigned, while the owner, thisObject, and the initial delegate are covered in
     * {@link InstrumentedGroovyMetaClassHelper#addInvocationHooksToEffectivelyInstrumentClosure(Closure, BytecodeInterceptorFilter)}
     */
    public static void addInvocationHooksInClosureDispatchObject(@Nullable Object object, boolean isEffectivelyInstrumented, BytecodeInterceptorFilter interceptorFilter) {
        if (object == null) {
            return;
        }
        if (isEffectivelyInstrumented) {
            CallInterceptorResolver resolver = ClosureCallInterceptorResolver.of(interceptorFilter);
            addInvocationHooksToMetaClass(object.getClass(), resolver);
        }
    }

    public static void addInvocationHooksToEffectivelyInstrumentClosure(Closure<?> closure, BytecodeInterceptorFilter interceptorFilter) {
        CallInterceptorResolver resolver = ClosureCallInterceptorResolver.of(interceptorFilter);
        addInvocationHooksToMetaClass(closure.getThisObject().getClass(), resolver);
        addInvocationHooksToMetaClass(closure.getOwner().getClass(), resolver);
        if (closure.getDelegate() != null) {
            addInvocationHooksToMetaClass(closure.getDelegate().getClass(), resolver);
        }
    }

    @SuppressWarnings("unused") // resolved via a method handle
    public static void addInvocationHooksToMetaClassIfInstrumented(Class<?> javaClass, String callableName) {
        // In BeanDynamicObject we can't filter interceptors, so we have to apply all interceptors
        CallInterceptorResolver resolver = ClosureCallInterceptorResolver.of(ALL);
        if (resolver.isAwareOfCallSiteName(callableName)) {
            addInvocationHooksToMetaClass(javaClass, resolver);
        }
    }

    private static void addInvocationHooksToMetaClass(Class<?> javaClass, CallInterceptorResolver resolver) {
        MetaClassRegistry metaClassRegistry = GroovySystem.getMetaClassRegistry();

        MetaClass originalMetaClass = metaClassRegistry.getMetaClass(javaClass);
        if (!(originalMetaClass instanceof CallInterceptingMetaClass)) {
            metaClassRegistry.setMetaClass(javaClass, interceptedMetaClass(javaClass, metaClassRegistry, originalMetaClass, resolver));
        }
    }

    private static CallInterceptingMetaClass interceptedMetaClass(Class<?> javaClass, MetaClassRegistry metaClassRegistry, MetaClass originalMetaClass, CallInterceptorResolver resolver) {
        return new CallInterceptingMetaClass(metaClassRegistry, javaClass, originalMetaClass, InstrumentedGroovyCallsHelper.INSTANCE, resolver);
    }
}
