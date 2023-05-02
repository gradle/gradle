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

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import org.gradle.api.NonNullApi;

/**
 * Injects the logic for Groovy calls instrumentation into the Groovy metaclasses.
 */
@SuppressWarnings("unused") // used in generated code
@NonNullApi
public class InstrumentedGroovyMetaClassHelper {
    /**
     * Should be invoked on an object that a Groovy Closure can dispatch the calls to. Injects the call interception logic into the metaclass of that object.
     * This is normally done for closure delegates, while the owner and thisObject are covered in {@link InstrumentedGroovyMetaClassHelper#addInvocationHooksInClosureConstructor(Object, Object)}
     */
    public static void addInvocationHooksInClosureDispatchObject(Object object) {
        addInvocationHooksToMetaClass(object.getClass());
    }

    /**
     * Should be invoked on a closure's owner and thisObject in the course of its constructor. Adds Groovy call interception logic
     * to the metaclasses of the two objects.
     */
    public static void addInvocationHooksInClosureConstructor(Object owner, Object thisObject) {
        addInvocationHooksToMetaClass(owner.getClass());
        addInvocationHooksToMetaClass(thisObject.getClass());
    }

    public static void addInvocationHooksToMetaClass(Class<?> javaClass) {
        MetaClassRegistry metaClassRegistry = GroovySystem.getMetaClassRegistry();

        MetaClass originalMetaClass = metaClassRegistry.getMetaClass(javaClass);
        if (!(originalMetaClass instanceof CallInterceptingMetaClass)) {
            metaClassRegistry.setMetaClass(javaClass, interceptedMetaClass(javaClass, metaClassRegistry, originalMetaClass));
        }
    }

    private static CallInterceptingMetaClass interceptedMetaClass(Class<?> javaClass, MetaClassRegistry metaClassRegistry, MetaClass originalMetaClass) {
        return new CallInterceptingMetaClass(metaClassRegistry, javaClass, originalMetaClass, Instrumented.INTERCEPTOR_RESOLVER);
    }
}
