/*
 * Copyright 2016 the original author or authors.
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



package org.gradle.process.internal.worker.child

import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilationUnit.ClassgenCallback
import org.codehaus.groovy.control.Phases
import org.gradle.api.Action
import org.gradle.internal.reflect.JavaReflectionUtil

import java.util.concurrent.atomic.AtomicInteger

class SerializableMockHelper {     static final Map ACTIONS = [:]
    private final AtomicInteger counter = new AtomicInteger()

    /**
     * Injects a proxy class into the target ClassLoader, so that when the given action is deserialized using the target
     * classloader, the proxy class executes the given action.
     */
    def <T> Action<T> serializable(Action<T> action, ClassLoader target) {
        String src = """
class TestAction implements ${Action.class.name}, ${Serializable.class.name}
{
    Object key

    void execute(Object target) {
        def action = ${SerializableMockHelper.class.name}.ACTIONS.remove(key)
        action.execute(target)
    }
}
"""
        CompilationUnit unit = new CompilationUnit(new GroovyClassLoader(target))
        unit.addSource("action", src)
        ClassCollector collector = new ClassCollector(target: target)
        unit.setClassgenCallback(collector);
        unit.compile(Phases.CLASS_GENERATION);

        Object instance = JavaReflectionUtil.newInstance(collector.generated)
        instance.key = counter.getAndIncrement()
        ACTIONS[instance.key] = action
        return instance
    }
}

class ClassCollector implements ClassgenCallback {
    Class generated
    ClassLoader target

    void call(ClassVisitor classVisitor, ClassNode classNode) {
        def bytes = ((ClassWriter) classVisitor).toByteArray();
        generated = target.defineClass(classNode.getName(), bytes, 0, bytes.length)
    }
}

