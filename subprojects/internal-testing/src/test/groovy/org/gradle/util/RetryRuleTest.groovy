/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.util

import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.RetryRule.retryIf

@SuppressWarnings("GroovyUnreachableStatement")
class RetryRuleTest extends Specification {

    int iteration = 0;

    @Rule
    RetryRule retryRule = retryIf({ t -> t instanceof IOException });

    @Rule
    ExpectedFailureRule expectedFailureRule = new ExpectedFailureRule();

    def "should pass when expected exception happens once"() {
        when:
        throwOnFirstExecution(new IOException());

        then:
        true
    }

    @ExpectedFailure
    def "should fail when unexpected exception happens once"() {
        when:
        throwOnFirstExecution(new RuntimeException());

        then:
        true
    }

    @ExpectedFailure
    def "should fail when expected exception happens consistently"() {
        when:
        throw new IOException();

        then:
        true
    }

    private void throwOnFirstExecution(Throwable throwable) {
        if (iteration % 2 == 0) {
            iteration++;
            throw throwable;
        }
    }
}
