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

package org.gradle.api.reporting.dependencies.internal;

import org.gradle.internal.vulnerability.DependencyHealthAnalyzer;

import java.util.Arrays;
import java.util.List;

public class DefaultDependencyHealthAnalyzer implements DependencyHealthAnalyzer {
    @Override
    public HealthReport analyze(String group, String name, String version) {
        return new DefaultHealthReport(Math.abs((group + ":" + name + ":" + version).hashCode()));
    }

    private static class DefaultHealthReport implements HealthReport {

        private int number;

        private DefaultHealthReport(int number) {
            this.number = number;
        }

        @Override
        public List<Cve> getCves() {
            return Arrays.asList(new DefaultCve(number));
        }
    }

    private static class DefaultCve implements Cve {
        private final String id;
        private final Severity severity;

        private DefaultCve(int number) {
            id = "CVE-2020-" + number;
            int computedScore = number % 100;
            double score = (computedScore == 0 ? 10 : computedScore) / 10.0;
            if (score < 3.9) {
                severity = Severity.LOW;
            } else if (score < 6.9) {
                severity = Severity.MEDIUM;
            } else if (score < 8.9) {
                severity = Severity.HIGH;
            } else {
                severity = Severity.CRITICAL;
            }
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Severity getSeverity() {
            return severity;
        }
    }
}
