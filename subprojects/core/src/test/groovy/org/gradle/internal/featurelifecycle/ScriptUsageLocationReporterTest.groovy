/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.featurelifecycle

import org.gradle.groovy.scripts.ScriptSource
import spock.lang.Specification
import org.gradle.groovy.scripts.Script

class ScriptUsageLocationReporterTest extends Specification {
    final reporter = new ScriptUsageLocationReporter()

    def "does nothing when no caller is a script"() {
        def stack = [
                new StackTraceElement("SomeClass", "method", "SomeClass.java", 12),
                new StackTraceElement("SomeClass", "method", "SomeClass.java", 77)]
        def usage = Stub(DeprecatedFeatureUsage) {
            getStack() >> stack
        }
        def result = new StringBuilder()

        when:
        reporter.reportLocation(usage, result)

        then:
        result.length() == 0
    }

    def "reports location when immediate caller is a script"() {
        def scriptSource = Stub(ScriptSource) {
            getFileName() >> "some-file.gradle"
            getDisplayName() >> "build script 'some-file.gradle'"
        }
        def script = Stub(Script) {
            getScriptSource() >> scriptSource
        }
        def stack = [
                new StackTraceElement("SomeClass", "method", "some-file.gradle", 12),
                new StackTraceElement("SomeClass", "method", "some-file.gradle", 77)]
        def usage = Stub(DeprecatedFeatureUsage) {
            getStack() >> stack
        }
        def result = new StringBuilder()

        given:
        reporter.beforeScript(script)
        reporter.afterScript(script, null)

        when:
        reporter.reportLocation(usage, result)

        then:
        result.toString() == /Build script 'some-file.gradle': line 12/
    }

    def "reports location when second caller is a script"() {
        def scriptSource = Stub(ScriptSource) {
            getFileName() >> "some-file.gradle"
            getDisplayName() >> "build script 'some-file.gradle'"
        }
        def script = Stub(Script) {
            getScriptSource() >> scriptSource
        }
        def stack = [
                new StackTraceElement("SomeLibrary", "method", "SomeLibrary.java", 103),
                new StackTraceElement("SomeLibrary", "method", "SomeLibrary.java", 67),
                new StackTraceElement("SomeClass", "method", "some-file.gradle", 12)
                ]
        def usage = Stub(DeprecatedFeatureUsage) {
            getStack() >> stack
        }
        def result = new StringBuilder()

        given:
        reporter.beforeScript(script)
        reporter.afterScript(script, null)

        when:
        reporter.reportLocation(usage, result)

        then:
        result.toString() == /Build script 'some-file.gradle': line 12/
    }

    def "does not report location when subsequent caller is a script"() {
        def scriptSource = Stub(ScriptSource) {
            getFileName() >> "some-file.gradle"
            getDisplayName() >> "build script 'some-file.gradle'"
        }
        def script = Stub(Script) {
            getScriptSource() >> scriptSource
        }
        def stack = [
                new StackTraceElement("SomeLibrary", "method", "SomeLibrary.java", 103),
                new StackTraceElement("OtherLibrary", "method", "OtherLibrary.java", 67),
                new StackTraceElement("SomeClass", "method", "some-file.gradle", 12)
                ]
        def usage = Stub(DeprecatedFeatureUsage) {
            getStack() >> stack
        }
        def result = new StringBuilder()

        given:
        reporter.beforeScript(script)
        reporter.afterScript(script, null)

        when:
        reporter.reportLocation(usage, result)

        then:
        result.length() == 0
    }
}
