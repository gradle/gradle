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

package org.gradle.internal.classpath

import org.gradle.internal.metaobject.BeanDynamicObject

import java.lang.reflect.Constructor
import java.util.function.Predicate

class GroovyDynamicDispatchingInterceptingTest extends AbstractCallInterceptionTest {

    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        InstrumentedClasses.nestedClassesOf(this.class)
    }

    @Override
    protected JvmBytecodeInterceptorSet jvmBytecodeInterceptorSet() {
        JvmBytecodeInterceptorSet.DEFAULT
    }

    @Override
    protected GroovyCallInterceptorsProvider groovyCallInterceptors() {
        GroovyCallInterceptorsProvider.DEFAULT
    }

    // Define nested classes so that we can freely modify the metaClass of these classes loaded in the child class loader
    class NestedClassOne {}

    class NestedClassTwo {}

    def 'setting a delegate of a closure replaces the metaclass of the delegate'() {
        given:
        def transformedClosure = instrumentedClasses.instrumentedClosure {}

        when:
        def modifiedMetaClass = instrumentedClasses.instrumentedClosure { Closure<?> closure ->
            closure.delegate = new NestedClassOne()
            GroovySystem.metaClassRegistry.getMetaClass(NestedClassOne.class) instanceof CallInterceptingMetaClass
        }(transformedClosure)

        then:
        modifiedMetaClass
    }

    def 'invoking a closure constructor with a specific owner replaces the metaclass of the delegate'() {
        given:
        def transformedClosureClass = instrumentedClasses.instrumentedClass({}.class)
        def constructor = transformedClosureClass.getDeclaredConstructor(Object.class, Object.class)

        when:
        def modifiedMetaClasses = instrumentedClasses.instrumentedClosure { Constructor<?> ctor ->
            ctor.newInstance(new NestedClassOne(), new NestedClassTwo())
            [
                GroovySystem.metaClassRegistry.getMetaClass(NestedClassOne.class) instanceof CallInterceptingMetaClass,
                GroovySystem.metaClassRegistry.getMetaClass(NestedClassTwo.class) instanceof CallInterceptingMetaClass
            ]
        }(constructor)

        then:
        modifiedMetaClasses.every()
    }

    def 'constructing a BeanDynamicObject replaces the metaclass of the bean'() {
        when:
        def modifiedMetaClasses = instrumentedClasses.instrumentedClosure {
            def instanceOne = new NestedClassOne()
            new BeanDynamicObject(instanceOne)
            def instanceTwo = new NestedClassTwo()
            new BeanDynamicObject(instanceTwo, Object)
            [
                instanceOne.metaClass instanceof CallInterceptingMetaClass,
                instanceTwo.metaClass instanceof CallInterceptingMetaClass
            ]
        }

        then:
        modifiedMetaClasses.every()
    }
}
