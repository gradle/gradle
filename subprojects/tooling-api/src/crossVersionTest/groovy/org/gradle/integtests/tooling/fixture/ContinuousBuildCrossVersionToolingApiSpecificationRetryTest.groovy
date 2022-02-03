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

package org.gradle.integtests.tooling.fixture

@TargetGradleVersion("<7.5")
class ContinuousBuildCrossVersionToolingApiSpecificationRetryTest extends ContinuousBuildToolingApiSpecification {

    def iteration = 0

    def "retries if continuous build test times out"() {
        given:
        iteration++

        when:
        throwWhen(new RuntimeException("Timeout waiting for build to complete."), iteration == 1)

        then:
        true
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable
        }
    }
}
