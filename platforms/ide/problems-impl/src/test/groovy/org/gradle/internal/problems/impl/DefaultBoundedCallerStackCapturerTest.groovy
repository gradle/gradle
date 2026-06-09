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

    def scripts = Stub(RegisteredScripts) {
        scriptFor(_) >> { String fileName -> fileName in ['build.gradle', 'other.gradle'] ? Stub(ClassLoaderScopeOrigin.Script) : null }
    }
    def capturer = new DefaultBoundedCallerStackCapturer(scripts)

    def "prefix starts at the first user-code frame"() {
        when:
        def throwable = StackCapturerCaller.siteA(capturer)

        then:
        throwable.stackTrace[0].className == 'acme.StackCapturerCaller'
        throwable.stackTrace[0].methodName == 'siteA'
    }

    def "repeat captures from the same site reuse one cached prefix"() {
        when:
        def captures = (1..2).collect { StackCapturerCaller.siteA(capturer) }

        then:
        capturer.cachedSiteCount() == 1
        captures[0].stackTrace*.toString() == captures[1].stackTrace*.toString()
    }

    def "distinct sites are each captured, regardless of how many times another site fires first"() {
        when:
        10.times { StackCapturerCaller.siteA(capturer) }
        def late = StackCapturerCaller.siteB(capturer)

        then:
        capturer.cachedSiteCount() == 2
        late.stackTrace[0].methodName == 'siteB'
    }

    def "distinct sites sharing a line-less nearest user frame are not conflated"() {
        given:
        // Nearest user frame has no line (synthetic); location comes from the deeper, differing frame.
        def nearest = frame('acme.Helper', 'doCall', 7, ste('acme.Helper', 'doCall', 'Helper.groovy', -1))
        def boundary = frame('org.gradle.Apply', 'apply', 1, ste('org.gradle.Apply', 'apply', 'Apply.java', 100))
        def internal = frame('org.gradle.Machinery', 'nag', 1, ste('org.gradle.Machinery', 'nag', 'Machinery.java', 5))
        def siteA = [internal, nearest, frame('acme.SiteA', 'run', 3, ste('acme.SiteA', 'run', 'SiteA.groovy', 20)), boundary]
        def siteB = [internal, nearest, frame('acme.SiteB', 'run', 3, ste('acme.SiteB', 'run', 'SiteB.groovy', 30)), boundary]

        when:
        capturer.cachedReducedStack(siteA.stream())
        def b = capturer.cachedReducedStack(siteB.stream())

        then:
        capturer.cachedSiteCount() == 2
        b*.fileName.contains('SiteB.groovy')
        !b*.fileName.contains('SiteA.groovy')
    }

    def "distinct call-sites sharing a non-script nearest frame resolve to their own script, not the first one cached"() {
        given:
        // A compiled helper (not a registered script) is the nearest user frame for both call-sites.
        // The location must come from each call-site's own script frame below it, not from whichever was cached first.
        def helper = frame('com.example.Checks', 'validate', 1, ste('com.example.Checks', 'validate', 'Checks.groovy', 12))
        def boundary = frame('org.gradle.api.internal.project.DefaultProject', 'evaluate', 1, ste('org.gradle.api.internal.project.DefaultProject', 'evaluate', 'DefaultProject.java', 100))
        def fromBuild = [helper, frame('build_gradle', 'run', 5, ste('build_gradle', 'run', 'build.gradle', 5)), boundary]
        def fromOther = [helper, frame('other_gradle', 'run', 9, ste('other_gradle', 'run', 'other.gradle', 9)), boundary]

        when:
        capturer.cachedReducedStack(fromBuild.stream())
        def other = capturer.cachedReducedStack(fromOther.stream())

        then:
        capturer.cachedSiteCount() == 2
        other*.fileName.contains('other.gradle')
        !other*.fileName.contains('build.gradle')
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
