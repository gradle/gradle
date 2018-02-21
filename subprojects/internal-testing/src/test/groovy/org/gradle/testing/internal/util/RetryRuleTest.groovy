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

package org.gradle.testing.internal.util

import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.testing.internal.util.RetryRule.retryIf

@SuppressWarnings("GroovyUnreachableStatement")
class RetryRuleTest extends Specification {

    @ClassRule
    @Shared
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Rule
    RetryRule retryRule = retryIf({ t -> t instanceof IOException })

    @Rule
    ExpectedFailureRule expectedFailureRule = new ExpectedFailureRule()

    final complexField = []
    int simpleField = 1

    int iteration

    def setup() {
        def specificationFile = new File(temporaryFolder.root, specificationContext.currentFeature.name)
        if (!specificationFile.exists()) {
            specificationFile.createNewFile()
            specificationFile.text = "1"
        }
        iteration = specificationFile.text as Integer
        specificationFile.text = iteration + 1
    }

    def "should pass when expected exception happens once"() {
        when:
        throwWhen(new IOException(), iteration == 1)

        then:
        true
    }

    def "should pass when expected exception happens twice"() {
        when:
        throwWhen(new IOException(), iteration <= 2)

        then:
        true
    }

    def "should retry and pass when spock expects a specific exception"() {
        when:
        throwWhen(new IOException(), iteration == 1)
        throwWhen(new RuntimeException("test"), iteration == 2)

        then:
        RuntimeException ex = thrown()
        ex.message.contains("test")
    }

    @ExpectedFailure(expected = RetryFailure.class)
    def "should fail when expected exception happens three times"() {
        when:
        throwWhen(new IOException(), iteration <= 3)

        then:
        true
    }

    @ExpectedFailure(expected = RuntimeException.class)
    def "should fail when expected exception happens once and another exception happens on next execution"() {
        when:
        throwWhen(new IOException(), iteration == 1)
        throwWhen(new RuntimeException(), iteration == 2)

        then:
        true
    }

    @ExpectedFailure(expected = RetryFailure.class)
    def "should fail when expected exception happens consistently"() {
        when:
        throw new IOException()

        then:
        true
    }

    @ExpectedFailure(expected = RuntimeException.class)
    def "should fail when unexpected exception happens once"() {
        when:
        throwWhen(new RuntimeException(), iteration == 1)

        then:
        true
    }

    def "re-runs field initializers"() {
        when:
        complexField.add("1")
        simpleField ++
        throwWhen(new IOException(), iteration <= 2)

        then:
        complexField.size() == 1
        simpleField == 2
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable
        }
    }
}
