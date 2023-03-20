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

package org.gradle.smoketests

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

class FreefairAspectJPluginSmokeTest extends AbstractPluginValidatingSmokeTest {
    // AspectJ does not support JDK17 yet
    @Requires(TestPrecondition.JDK16_OR_EARLIER)
    @Issue('https://plugins.gradle.org/plugin/io.freefair.aspectj')
    def 'freefair aspectj plugin'() {
        given:
        buildFile << """
            plugins {
                id "java-library"
                id "io.freefair.aspectj" version "${TestedVersions.aspectj}"
            }

            ${mavenCentralRepository()}

            dependencies {
                inpath "org.apache.httpcomponents:httpcore-nio:4.4.11"
                implementation "org.aspectj:aspectjrt:1.9.7"

                testImplementation "junit:junit:4.13"
            }
        """
        file("src/main/aspectj/StupidAspect.aj") << """
            import org.aspectj.lang.ProceedingJoinPoint;
            import org.aspectj.lang.annotation.Around;
            import org.aspectj.lang.annotation.Aspect;

            @Aspect
            public class StupidAspect {
                @Around("execution(* org.apache.http.util.Args.*(..))")
                public Object stupidAdvice(ProceedingJoinPoint joinPoint) {
                    throw new RuntimeException("Doing stupid things");
                }
            }
        """
        file("src/test/java/StupidAspectTest.aj") << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class StupidAspectTest {
                @Test
                public void stupidAdvice() {
                    try {
                        org.apache.http.util.Args.check(true, "foo");
                        fail();
                    } catch (Exception e) {
                        assertTrue(e.getMessage().contains("stupid"));
                    }
                }
            }
        """

        expect:
        runner('check')
            .forwardOutput()
            .build()
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'io.freefair.aspectj': Versions.of(TestedVersions.aspectj),
            'io.freefair.aspectj.post-compile-weaving': Versions.of(TestedVersions.aspectj)
        ]
    }
}
