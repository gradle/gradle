/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.logging;

import org.gradle.samples.executor.ExecutionMetadata;
import org.gradle.samples.test.normalizer.OutputNormalizer;

/*
======= Failed test run #1 ==========
java.lang.AssertionError: Extra lines in output.

w: /home/user/gradle/samples/app/src/main/kotlin/demo/App.kt: (13, 10): Parameter 'args' is never used
---
Actual output:

> Task :app:compileKotlin
w: /home/user/gradle/samples/app/src/main/kotlin/demo/App.kt: (13, 10): Parameter 'args' is never used

...

Because by default gradle init generates the following code:

fun main(args: Array<String>) {
    println(App().greeting)
}
 */
public class SuppressingKotlinUnusedParameterWarningNormalizer implements OutputNormalizer {
    @Override
    public String normalize(String s, ExecutionMetadata executionMetadata) {
        if (executionMetadata.getTempSampleProjectDir().getAbsolutePath().contains("building-kotlin-applications")) {
            // Remove the warning line and first empty line
            return s.replaceAll("w:.*Parameter 'args' is never used\\s+", "").trim();
        }

        return s;
    }
}
