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

package org.gradle.internal.problems


import org.gradle.initialization.ClassLoaderScopeId
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager
import spock.lang.Specification

class DefaultProblemLocationAnalyzerTest extends Specification {
    def analyzer = new DefaultProblemLocationAnalyzer(Mock(ClassLoaderScopeRegistryListenerManager))
    def element = new StackTraceElement("class", "method", "filename", 7)
    def callerElement = new StackTraceElement("class", "method", "filename", 11)
    def otherElement = new StackTraceElement("class", "method", "otherfile", 11)
    def elementWithNoSourceFile = new StackTraceElement("class", "method", null, 11)
    def elementWithNoLineNumber = new StackTraceElement("class", "method", "filename", -1)

    def 'uses location info from deepest stack frame with matching source file and line information'() {
        given:
        analyzer.childScopeCreated(Stub(ClassLoaderScopeId), Stub(ClassLoaderScopeId), new ClassLoaderScopeOrigin.Script("filename", "<source>"))

        when:
        def location = analyzer.locationForUsage([elementWithNoSourceFile, elementWithNoLineNumber, otherElement, element, callerElement])

        then:
        location.sourceDisplayName == "<source>"
        location.lineNumber == 7
        location.formatted == "<source>: line 7"
    }
}
