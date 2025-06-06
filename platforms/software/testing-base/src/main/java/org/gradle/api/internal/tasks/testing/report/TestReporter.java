/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.report;

import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Configure custom logic to generate a test report.
 * <p>
 * <strong>This is an internal API</strong>
 * <p>
 * This interface is currently implemented and required by cashapp/paparazzi
 *
 * @see <a href="https://github.com/cashapp/paparazzi/blob/bf86c4f0ffe3da60cf1e0b4988c32e1c027f07a3/paparazzi-gradle-plugin/src/main/java/app/cash/paparazzi/gradle/PaparazziPlugin.kt#L290C40-L296">link</a>
 */
@NullMarked
public interface TestReporter {
    void generateReport(TestResultsProvider testResultsProvider, File reportDir);
}
