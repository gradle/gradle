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

package org.gradle.integtests.fixtures.logging

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

/**
 * Removes extra empty lines from `spring-boot-web-application` sample output.
 * Before:
 * <pre>
 > Task :app:compileJava

 > Task :app:processResources
 > Task :app:classes

 > Task :app:compileTestJava

 > Task :app:processTestResources NO-SOURCE
 > Task :app:testClasses
 > Task :app:test

 BUILD SUCCESSFUL in 0s
 4 actionable tasks: 4 executed
 </pre>
 *
 * After:
 <pre>
 > Task :app:compileJava
 > Task :app:processResources
 > Task :app:classes
 > Task :app:compileTestJava
 > Task :app:processTestResources NO-SOURCE
 > Task :app:testClasses
 > Task :app:test

 BUILD SUCCESSFUL in 0s
 4 actionable tasks: 4 executed
 </pre>
 * */
class SpringBootWebAppTestOutputNormalizer implements OutputNormalizer {

    @Override
    String normalize(String output, ExecutionMetadata executionMetadata) {
        return executionMetadata.tempSampleProjectDir.name.contains('building-spring-boot-web-applications')
            ? removeEmptyLinesBeforeBuildSuccessful(output)
            : output
    }

    private static String removeEmptyLinesBeforeBuildSuccessful(String output) {
        List<String> lines = output.readLines()
        int buildSuccessfulLineIndex = lines.indexOf("BUILD SUCCESSFUL in 0s")
        assert buildSuccessfulLineIndex != -1

        List<String> normalized = lines.subList(0, buildSuccessfulLineIndex).grep { !it.isEmpty() } + "" + lines.subList(buildSuccessfulLineIndex, lines.size())
        return normalized.join("\n")
    }
}
