/*
 * Copyright 2026 the original author or authors.
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

import acme.StackCapturerCaller
import org.gradle.initialization.ClassLoaderScopeOrigin
import spock.lang.Specification

import java.lang.StackWalker.StackFrame

class DefaultBoundedCallerStackCapturerTest extends Specification {

    // Checks.groovy and Helper.groovy stand in for compiled helpers (not registered scripts); everything else is a script.
    def scripts = Stub(RegisteredScripts) {
        scriptFor(_) >> { String fileName -> fileName in ['Checks.groovy', 'Helper.groovy'] ? null : Stub(ClassLoaderScopeOrigin.Script) }
    }
    def capturer = new DefaultBoundedCallerStackCapturer(scripts)

    def "reduced stack is cut at the call site and keeps the frames above it"() {
        when:
        def stack = StackCapturerCaller.siteA(capturer).stackTrace

        then:
        // The anchor is the call site; everything below it is dropped, so it is the last kept frame.
        stack[-1].className == 'acme.StackCapturerCaller'
        stack[-1].methodName == 'siteA'
    }

    def "each call site resolves to its own frame, captures are independent"() {
        when:
        def a = StackCapturerCaller.siteA(capturer).stackTrace
        def b = StackCapturerCaller.siteB(capturer).stackTrace

        then:
        a[-1].methodName == 'siteA'
        b[-1].methodName == 'siteB'
    }

    def "keeps every frame above the script and drops everything below it"() {
        given:
        // top -> bottom: reporting machinery, build-logic helper, the script, then the Gradle boundary and below.
        def reporting = frame('org.gradle.api.problems.internal.DefaultProblemReporter', 'report', 1, ste('org.gradle.api.problems.internal.DefaultProblemReporter', 'report', 'DefaultProblemReporter.java', 58))
        def helper = frame('com.example.Checks', 'validate', 1, ste('com.example.Checks', 'validate', 'Checks.groovy', 12))
        def script = frame('build_gradle', 'run', 5, ste('build_gradle', 'run', 'build.gradle', 5))
        def boundary = frame('org.gradle.api.internal.project.DefaultProject', 'evaluate', 1, ste('org.gradle.api.internal.project.DefaultProject', 'evaluate', 'DefaultProject.java', 100))
        def belowBoundary = frame('org.gradle.launcher.Main', 'main', 1, ste('org.gradle.launcher.Main', 'main', 'Main.java', 50))

        when:
        def reduced = capturer.reduceStack([reporting, helper, script, boundary, belowBoundary].stream())

        then:
        // Reporting entry point and build-logic helper kept above the script; nothing from the boundary down.
        reduced*.fileName == ['DefaultProblemReporter.java', 'Checks.groovy', 'build.gradle']
    }

    def "sees through a non-script helper to the script below it, keeping the helper as context"() {
        given:
        def helper = frame('com.example.Checks', 'validate', 1, ste('com.example.Checks', 'validate', 'Checks.groovy', 12))
        def script = frame('build_gradle', 'run', 5, ste('build_gradle', 'run', 'build.gradle', 5))
        def boundary = frame('org.gradle.api.internal.project.DefaultProject', 'evaluate', 1, ste('org.gradle.api.internal.project.DefaultProject', 'evaluate', 'DefaultProject.java', 100))

        when:
        def reduced = capturer.reduceStack([helper, script, boundary].stream())

        then:
        reduced[-1].fileName == 'build.gradle'             // anchored on (cut at) the script
        reduced*.fileName.contains('Checks.groovy')        // helper kept above it
    }

    def "distinct call-sites sharing a non-script helper each resolve to their own script"() {
        given:
        // A compiled helper (not a registered script) is the nearest user frame for both call-sites.
        // Each capture is computed from its own stack, so neither can be attributed to the other's script.
        def helper = frame('com.example.Checks', 'validate', 1, ste('com.example.Checks', 'validate', 'Checks.groovy', 12))
        def boundary = frame('org.gradle.api.internal.project.DefaultProject', 'evaluate', 1, ste('org.gradle.api.internal.project.DefaultProject', 'evaluate', 'DefaultProject.java', 100))
        def fromBuild = [helper, frame('build_gradle', 'run', 5, ste('build_gradle', 'run', 'build.gradle', 5)), boundary]
        def fromOther = [helper, frame('other_gradle', 'run', 9, ste('other_gradle', 'run', 'other.gradle', 9)), boundary]

        when:
        def build = capturer.reduceStack(fromBuild.stream())
        def other = capturer.reduceStack(fromOther.stream())

        then:
        build*.fileName.contains('build.gradle')
        !build*.fileName.contains('other.gradle')
        other*.fileName.contains('other.gradle')
        !other*.fileName.contains('build.gradle')
    }

    def "with no script, resolves to the nearest user frame and drops the boundary"() {
        given:
        def helper = frame('com.example.Checks', 'validate', 1, ste('com.example.Checks', 'validate', 'Checks.groovy', 12))
        def boundary = frame('org.gradle.api.internal.project.DefaultProject', 'evaluate', 1, ste('org.gradle.api.internal.project.DefaultProject', 'evaluate', 'DefaultProject.java', 100))

        when:
        def reduced = capturer.reduceStack([helper, boundary].stream())

        then:
        reduced[-1].fileName == 'Checks.groovy'              // nearest user frame, no script below it
        !reduced*.fileName.contains('DefaultProject.java')   // boundary dropped
    }

    def "a line-less user frame is not the anchor, the script below it is"() {
        given:
        // Nearest user frame has no line (synthetic); the location comes from the deeper script frame.
        def lineless = frame('acme.Helper', 'doCall', 7, ste('acme.Helper', 'doCall', 'Helper.groovy', -1))
        def script = frame('acme.SiteA', 'run', 3, ste('acme.SiteA', 'run', 'SiteA.groovy', 20))
        def boundary = frame('org.gradle.Apply', 'apply', 1, ste('org.gradle.Apply', 'apply', 'Apply.java', 100))

        when:
        def reduced = capturer.reduceStack([lineless, script, boundary].stream())

        then:
        reduced[-1].fileName == 'SiteA.groovy'
        reduced*.fileName.contains('Helper.groovy')   // the line-less frame is kept above the anchor
    }

    def "nested scripts resolve to the innermost script and drop the outer one below it"() {
        given:
        // foo.gradle is applied from build.gradle and fires the problem; the location is the innermost script.
        def inner = frame('foo_gradle', 'run', 10, ste('foo_gradle', 'run', 'foo.gradle', 10))
        def outer = frame('build_gradle', 'run', 3, ste('build_gradle', 'run', 'build.gradle', 3))
        def boundary = frame('org.gradle.api.internal.project.DefaultProject', 'evaluate', 1, ste('org.gradle.api.internal.project.DefaultProject', 'evaluate', 'DefaultProject.java', 100))

        when:
        def reduced = capturer.reduceStack([inner, outer, boundary].stream())

        then:
        reduced[-1].fileName == 'foo.gradle'            // cut at the innermost script
        !reduced*.fileName.contains('build.gradle')     // the outer script below the anchor is dropped
    }

    def "a stack with no user frame yields no capture"() {
        given:
        def allInternal = [
            frame('org.gradle.internal.deprecation.DeprecationLogger', 'nag', 1, ste('org.gradle.internal.deprecation.DeprecationLogger', 'nag', 'DeprecationLogger.java', 50)),
            frame('org.gradle.api.internal.project.DefaultProject', 'evaluate', 1, ste('org.gradle.api.internal.project.DefaultProject', 'evaluate', 'DefaultProject.java', 100))
        ]

        expect:
        capturer.reduceStack(allInternal.stream()) == null
    }

    private StackFrame frame(String className, String methodName, int bci, StackTraceElement element) {
        Stub(StackFrame) {
            getClassName() >> className
            getMethodName() >> methodName
            getByteCodeIndex() >> bci
            getDescriptor() >> '()V'
            toStackTraceElement() >> element
        }
    }

    private static StackTraceElement ste(String declaringClass, String methodName, String fileName, int line) {
        new StackTraceElement(declaringClass, methodName, fileName, line)
    }
}
