/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE })
class JUnitClassDetectionIntegrationTest extends JUnitMultiVersionIntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { ${dependencyNotation.collect { "testImplementation '$it'" }.join('\n')} }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/18465")
    def 'does not try to execute non-test class as test class'() {
        given:
        file('src/test/java/com/example/MyTest.java') << '''
package com.example;
import org.junit.Test;
import static org.junit.Assert.*;

public class MyTest {
    @Test public void someTest() {
        assertTrue(true);
    }
}
'''
        file('src/test/java/com/example/CacheSpec.java') << '''
package com.example;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("ImmutableEnumChecker")
@Target(METHOD) @Retention(RUNTIME)
public @interface CacheSpec {

  enum CacheExecutor {
    DEFAULT {
      @Override public Executor create() {
        return ForkJoinPool.commonPool();
      }
    },
    DIRECT {
      @Override public Executor create() {
        return Runnable::run;
      }
    };

    public abstract Executor create();
  }
}
'''
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory).testClass('com.example.MyTest').assertTestCount(1, 0, 0)
    }
}
