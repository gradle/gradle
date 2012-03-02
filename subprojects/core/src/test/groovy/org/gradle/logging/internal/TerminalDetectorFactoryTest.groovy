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


import org.gradle.internal.nativeplatform.NoOpTerminalDetector
import org.gradle.internal.nativeplatform.WindowsTerminalDetector
import org.gradle.internal.nativeplatform.jna.JnaBootPathConfigurer
import org.gradle.internal.nativeplatform.jna.LibCBackedTerminalDetector
import org.gradle.util.Requires
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 9/12/11
 */
public class TerminalDetectorFactoryTest extends Specification {
    @Rule
    TemporaryFolder temp = new TemporaryFolder()

    @Requires([TestPrecondition.JNA, TestPrecondition.NOT_WINDOWS])
    def "should configure JNA library"() {
        when:
        def spec = new TerminalDetectorFactory().create(new JnaBootPathConfigurer(temp.dir))

        then:
        spec instanceof LibCBackedTerminalDetector
    }

    @Requires([TestPrecondition.JNA, TestPrecondition.WINDOWS])
    def "should configure JNA library on Windows"() {
        when:
        def spec = new TerminalDetectorFactory().create(new JnaBootPathConfigurer(temp.dir))

        then:
        spec instanceof WindowsTerminalDetector
    }

    @Issue("GRADLE-1776")
    @Requires(TestPrecondition.NO_JNA)
    def "should assume no terminal is available when JNA library is not available"() {
        when:
        def spec = new TerminalDetectorFactory().create(new JnaBootPathConfigurer(temp.dir))

        then:
        spec instanceof NoOpTerminalDetector
    }
}
