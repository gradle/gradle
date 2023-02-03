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

package org.gradle.api.internal.tasks.compile

import com.google.common.base.Throwables
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import spock.lang.Issue
import spock.lang.Specification

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import javax.tools.JavaCompiler
import java.lang.reflect.InvocationTargetException

class AnnotationProcessingCompileTaskTest extends Specification {

    @Issue("gradle/gradle#8996")
    def "stacktrace contains root cause"() {
        given:
        def proc = new AnnotationProcessorDeclaration(processorClassName, IncrementalAnnotationProcessorType.UNKNOWN)
        def processTask = createProcessingTask(proc)

        when:
        processTask.call()

        then:
        def e = thrown(Throwable)
        def root = Throwables.getRootCause(e)
        root.message == errorMsg
        root.stackTrace.any { frame -> frame.className == throwingClass }
        !Throwables.getCausalChain(e).any { c -> c.class == InvocationTargetException.class }

        where:
        processorClassName              | throwingClass                      | errorMsg
        FaultyProcessor.class.getName() | FaultyProcessor.class.getName()    | "Whoops!"
        "you.wont.find.Me"              | LimitedClassLoader.class.getName() | "Unexpected class load: you.wont.find.Me"
    }

    def createProcessingTask(AnnotationProcessorDeclaration proc) {
        def noopClassloader = Stub(ClassLoader)
        return new AnnotationProcessingCompileTask(Stub(JavaCompiler.CompilationTask), [proc] as Set<AnnotationProcessorDeclaration>, [], Stub(AnnotationProcessingResult)) {
            ClassLoader createProcessorClassLoader() {
                return new LimitedClassLoader(noopClassloader)
            }
        }
    }

}

class LimitedClassLoader extends ClassLoader {
    LimitedClassLoader(ClassLoader parent) {
        super(parent)
    }

    @Override
    Class<?> loadClass(String name) throws ClassNotFoundException {
        if (FaultyProcessor.class.getName() == name) {
            return FaultyProcessor.class
        } else {
            throw new ClassNotFoundException("Unexpected class load: " + name)
        }
    }
}

class FaultyProcessor extends AbstractProcessor {
    FaultyProcessor() {
        throw new RuntimeException("Whoops!")
    }

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        return false
    }
}
