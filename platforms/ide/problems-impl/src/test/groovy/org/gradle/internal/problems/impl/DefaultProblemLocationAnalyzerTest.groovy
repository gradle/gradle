/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.problems.impl

import org.gradle.initialization.ClassLoaderScopeId
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager
import org.gradle.internal.Describables
import org.gradle.internal.problems.failure.DefaultFailureFactory
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.StackTraceRelevance
import spock.lang.Specification

class DefaultProblemLocationAnalyzerTest extends Specification {
    def analyzer = new DefaultProblemLocationAnalyzer(Mock(ClassLoaderScopeRegistryListenerManager))
    def element = new StackTraceElement("class", "method", "filename", 7)
    def callerElement = new StackTraceElement("class", "method", "filename", 11)
    def otherElement = new StackTraceElement("class", "method", "otherfile", 11)
    def elementWithNoSourceFile = new StackTraceElement("class", "method", null, 11)
    def elementWithNoLineNumber = new StackTraceElement("class", "method", "filename", -1)

    def 'uses location info from deepest stack frame with matching source file and line information'() {
        def stack = [elementWithNoSourceFile, elementWithNoLineNumber, otherElement, element, callerElement]
        def failure = Mock(Failure) {
            getStackTrace() >> stack
            getStackTraceRelevance(_) >> StackTraceRelevance.USER_CODE
            indexOfStackFrame(_, _) >> { start, predicate -> stack.findIndexOf(start) { predicate.test(it, StackTraceRelevance.USER_CODE) } }
        }
        def longDisplayName = Describables.of("<long source>")
        def shortDisplayName = Describables.of("<short source>")

        given:
        analyzer.childScopeCreated(Stub(ClassLoaderScopeId), Stub(ClassLoaderScopeId), new ClassLoaderScopeOrigin.Script("filename", longDisplayName, shortDisplayName))

        when:
        def location = analyzer.locationForUsage(failure, false)

        then:
        location.sourceLongDisplayName == longDisplayName
        location.sourceShortDisplayName == shortDisplayName
        location.lineNumber == 7
        location.formatted == "<long source>: line 7"
    }

    def "bounded stack prefix yields the same location as the full stack"() {
        given:
        def failureFactory = DefaultFailureFactory.withDefaultClassifier()
        def scriptFrame = new StackTraceElement("Build_gradle", "run", "build.gradle", 7)
        def fullStack = [
            new StackTraceElement("org.gradle.internal.problems.impl.DefaultBoundedCallerStackCapturer", "captureCallerStack", "DefaultBoundedCallerStackCapturer.java", 40),
            new StackTraceElement("org.gradle.internal.deprecation.DeprecationLogger", "nagUserWith", "DeprecationLogger.java", 300),
            scriptFrame,
            new StackTraceElement("org.gradle.api.internal.project.DefaultProject", "evaluate", "DefaultProject.java", 100),
            new StackTraceElement("org.gradle.launcher.Main", "main", "Main.java", 50)
        ] as StackTraceElement[]
        // What the bounded walk keeps: from the top down to and including the Gradle boundary after the first user frame.
        def boundedPrefix = (fullStack[0..3]) as StackTraceElement[]

        analyzer.childScopeCreated(Stub(ClassLoaderScopeId), Stub(ClassLoaderScopeId),
            new ClassLoaderScopeOrigin.Script("build.gradle", Describables.of("<long>"), Describables.of("<short>")))

        when:
        def full = analyzer.locationForUsage(failureFor(failureFactory, fullStack), false)
        def bounded = analyzer.locationForUsage(failureFor(failureFactory, boundedPrefix), false)

        then:
        full.lineNumber == 7
        bounded.lineNumber == full.lineNumber
        bounded.formatted == full.formatted
        bounded.sourceLongDisplayName == full.sourceLongDisplayName
    }

    private static Failure failureFor(DefaultFailureFactory failureFactory, StackTraceElement[] stack) {
        def throwable = new Exception()
        throwable.stackTrace = stack
        failureFactory.create(throwable)
    }
}
