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

package org.gradle.integtests.fixtures.executer

import spock.lang.Specification

class OutputScrapingExecutionFailureTest extends Specification {

    def "recreates exception stack trace"() {
        given:
        def errorOut = """
Some text before

* Exception is:
org.gradle.internal.service.ServiceCreationException: Could not create service of type CacheLockingManager
    at org.gradle.internal.service.DefaultServiceRegistry.some(DefaultServiceRegistry.java:604)
Caused by: java.io.IOException: Something in the middle
    at org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager.initMetaDataStoreDir(DefaultCacheLockingManager.java:59)
Caused by: org.gradle.api.UncheckedIOException: Unable to create directory 'metadata-2.1'
    at org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager.initMetaDataStoreDir(DefaultCacheLockingManager.java:59)
"""
        when:
        OutputScrapingExecutionFailure outputScraping = new OutputScrapingExecutionFailure("", errorOut)

        then:
        outputScraping.exception.class.simpleName == 'ServiceCreationException'
        outputScraping.exception.message == 'Could not create service of type CacheLockingManager'
        outputScraping.exception.cause.class.simpleName == 'IOException'
        outputScraping.exception.cause.message == 'Something in the middle'
        outputScraping.exception.cause.cause.class.simpleName == 'UncheckedIOException'
        outputScraping.exception.cause.cause.message == "Unable to create directory 'metadata-2.1'"
    }

}
