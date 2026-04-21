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

package org.gradle.integtests.fixtures.logging

import org.jspecify.annotations.Nullable
import spock.lang.Specification

class DependencyInsightOutputNormalizerTest extends Specification {

    def normalizer = new DependencyInsightOutputNormalizer()

    def 'normalizes provided and requested JDK version report'() {
        given:
        def originalOutput = """
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         | ${padRight(provided, 8)} | ${padRight(requested, 12)} |"""

        def expectedProvided = provided != null ? "11" : ""
        def expectedRequested = requested != null ? "11" : ""

        expect:
        normalizer.normalize(originalOutput, null) == """
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         | ${expectedProvided.padRight(8)} | ${expectedRequested.padRight(12)} |"""

        where:
        provided | requested
        null     | null
        8        | null
        17       | null
        null     | 8
        null     | 17
        8        | 8
        8        | 17
        17       | 8
        17       | 17
    }

    String padRight(@Nullable Integer value, int num) {
        String str
        if (value == null) {
            str = ""
        } else {
            str = Integer.toString(value)
        }
        str.padRight(num)
    }

}
