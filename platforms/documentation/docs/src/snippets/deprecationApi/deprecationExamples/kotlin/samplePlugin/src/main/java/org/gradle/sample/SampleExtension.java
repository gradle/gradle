/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.sample;

import org.gradle.api.problems.Problems;
import org.gradle.api.problems.deprecation.source.ReportSource;

import javax.inject.Inject;

public class SampleExtension {

    private final Problems problems;
    private String message;

    @Inject
    public SampleExtension(Problems problems) {
        this.problems = problems;
        problems.getDeprecationReporter().deprecate(
            ReportSource.plugin("org.gradle.sample.plugin"),
            "The extension 'org.gradle.sample.SampleExtension' is deprecated.",
            spec -> spec
                .removedInVersion("3.0.0")
                .details("The extension is no longer supported")
        );
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        problems.getDeprecationReporter().deprecateMethod(
            ReportSource.plugin("org.gradle.sample.plugin"),
            SampleExtension.class,
            "getMessage()",
            spec -> spec
                .removedInVersion("2.0.0")
                .replacedBy("A new object `message { }`, which is a better representation of the message")
        );
        this.message = message;
    }
}
