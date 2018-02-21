/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.util.GUtil
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.testing.internal.util.RetryRule.retryIf

class RetryRuleWithSetupRerunTest extends SuperSpecification {

    private static final String ITERATION_PROPERTY_NAME = "iteration"
    private static final String SETUP_CALL_COUNT_PROPERTY_NAME = "setupCallCount"

    @Rule
    RetryRule retryRule = retryIf({ t -> t instanceof IOException })

    int iteration

    int setupCallCount

    def setup() {
        def specificationFile = new File(temporaryFolder.root, specificationContext.currentFeature.name)
        if (!specificationFile.exists()) {
            specificationFile.createNewFile()
            def properties = new Properties()
            properties.setProperty(ITERATION_PROPERTY_NAME, "1")
            properties.setProperty(SETUP_CALL_COUNT_PROPERTY_NAME, "1")
            GUtil.saveProperties(properties, specificationFile)
        }
        def properties = GUtil.loadProperties(specificationFile)
        iteration = properties.getProperty(ITERATION_PROPERTY_NAME) as Integer
        setupCallCount = properties.getProperty(SETUP_CALL_COUNT_PROPERTY_NAME) as Integer

        properties.setProperty(ITERATION_PROPERTY_NAME, (iteration + 1).toString())
        properties.setProperty(SETUP_CALL_COUNT_PROPERTY_NAME, (setupCallCount + 1).toString())
        GUtil.saveProperties(properties, specificationFile)
    }

    def "reruns all setup methods if specification is passed to rule"() {
        when:
        throwWhen(new IOException(), iteration < 2)

        then:
        setupCallCount == 2
        superSetupCallCount == 2
    }

    def "reruns all setup methods if specification is passed to rule for both retries"() {
        when:
        throwWhen(new IOException(), iteration < 3)

        then:
        setupCallCount == 3
        superSetupCallCount == 3
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable
        }
    }
}

class SuperSpecification extends Specification {
    private static final String SUPER_SETUP_CALL_COUNT_PROPERTY_NAME = "setupCallCount"

    @Shared
    @ClassRule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    int superSetupCallCount

    def setup() {
        def specificationFile = new File(temporaryFolder.root, specificationContext.currentFeature.name + "super")
        if (!specificationFile.exists()) {
            specificationFile.createNewFile()
            def properties = new Properties()
            properties.setProperty(SUPER_SETUP_CALL_COUNT_PROPERTY_NAME, "1")
            GUtil.saveProperties(properties, specificationFile)
        }
        def properties = GUtil.loadProperties(specificationFile)
        superSetupCallCount = properties.getProperty(SUPER_SETUP_CALL_COUNT_PROPERTY_NAME) as Integer

        properties.setProperty(SUPER_SETUP_CALL_COUNT_PROPERTY_NAME, (superSetupCallCount + 1).toString())
        GUtil.saveProperties(properties, specificationFile)
    }
}

