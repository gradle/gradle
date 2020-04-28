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

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import org.hamcrest.BaseMatcher
import spock.lang.Issue
import spock.lang.Specification
import static spock.util.matcher.HamcrestSupport.that

import java.lang.annotation.ElementType
import java.lang.annotation.Target
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.JavaCompiler

class AnnotationProcessingCompileTaskTest extends Specification {

    List<File> procPath = Collections.emptyList()
    AnnotationProcessingResult result = Stub(AnnotationProcessingResult)
    JavaCompiler.CompilationTask compileTask = Stub(JavaCompiler.CompilationTask)
    ClassLoader noopLoader = Stub(ClassLoader)

    @Issue("gradle/gradle#8996")
    def "stacktrace contains root cause"() {
        given:
            def proc = new AnnotationProcessorDeclaration(processorClassName, IncrementalAnnotationProcessorType.UNKNOWN)
            def processTask = createProcessingTask(proc)

        when:
            processTask.call()

        then:
            def e = thrown(Throwable)
            def root = getRootCause(e)
            root.message.equals(errorMsg)
            that root.stackTrace, hasFrame(throwingClass)

        where:
            processorClassName              | throwingClass                      | errorMsg
            FaultyProcessor.class.getName() | FaultyProcessor.class.getName()    | "Whoops!"
            "you.wont.find.Me"              | LimitedClassLoader.class.getName() | "Unexpected class load: you.wont.find.Me"
    }

    def createProcessingTask(AnnotationProcessorDeclaration proc) {
        Set<AnnotationProcessorDeclaration> procs = Collections.singletonList(proc)
        return new AnnotationProcessingCompileTask(compileTask, procs, procPath, result) {
            ClassLoader createProcessorClassLoader() {
                return new LimitedClassLoader(noopLoader)
            }
        }
    }

    def getRootCause(Throwable t) {
        if (t.getCause() == null || t.getCause() == t) {
            return t
        }
        return getRootCause(t.getCause())
    }

    private hasFrame(final String className) {
        [
            matches: { frames ->
                def foundFrame = null

                for (StackTraceElement frame : frames) {
                    if (className.equals(frame.className)) {
                        foundFrame = frame
                    }
                }

                if (foundFrame == null) {
                    return false
                }

                assert foundFrame.fileName != null
                assert foundFrame.lineNumber > 0
                assert foundFrame.methodName != null
                return true
            },

            describeTo: { description ->
                description.appendText("stack trace contain frame for class ${className}")
            },

            describeMismatch: { frames, description ->
                description.appendValue(frames.toArrayString()).appendText(" did not have frame for class ${className}")
            }
        ] as BaseMatcher
    }
}

class LimitedClassLoader extends ClassLoader {
    LimitedClassLoader(ClassLoader parent) {
        super(parent)
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (FaultyProcessor.class.getName().equals(name)) {
            return FaultyProcessor.class
        } else {
            throw new ClassNotFoundException("Unexpected class load: " + name)
        }
    }
}

@Target(ElementType.TYPE)
@interface RunsFaultyProcessor {
    String QUALIFIED_NAME = "org.gradle.api.internal.tasks.compile.RunsFaultyProcessor"
}

@SupportedAnnotationTypes(RunsFaultyProcessor.QUALIFIED_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_6)
class FaultyProcessor extends AbstractProcessor {
    public FaultyProcessor() {
        throw new RuntimeException("Whoops!")
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        return false
    }
}
