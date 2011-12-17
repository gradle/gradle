/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.logging.internal;

import org.gradle.logging.internal.JnaBootPathConfigurer.JnaNotAvailableException
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestPrecondition
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 9/12/11
 */
public class TerminalDetectorFactoryTest extends Specification {
    @Rule
    public def temp = new TemporaryFolder()

    @Requires(TestPrecondition.JNA)
    def "should configure jna library"() {
        when:
        def spec = new TerminalDetectorFactory().create(new JnaBootPathConfigurer(temp.dir))

        then:
        spec instanceof TerminalDetector
    }

    @Issue("GRADLE-1776")
    def "should assume no terminal is available when jna library not found"() {
        given:
        def configurer = Mock(JnaBootPathConfigurer)
        configurer.configure() >> { throw new JnaNotAvailableException("foo") }

        when:
        def spec = new TerminalDetectorFactory().create(configurer)

        then:
        !spec.isSatisfiedBy(FileDescriptor.out)
        !spec.isSatisfiedBy(FileDescriptor.err)
    }
}
