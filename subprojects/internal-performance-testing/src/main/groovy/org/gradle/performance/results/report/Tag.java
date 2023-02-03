/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results.report;

import java.math.BigDecimal;

import static org.gradle.performance.results.report.Tag.FixedTag.UNTAGGED;

public interface Tag {
    String getName();

    String getClassAttr();

    String getTitle();

    String getUrl();

    default boolean isValid() {
        return this != UNTAGGED;
    }

    enum FixedTag implements Tag {
        FROM_CACHE("FROM-CACHE", "badge badge-info", "The test is not really executed - its results are fetched from build cache."),
        FAILED("FAILED", "badge badge-danger", "Regression confidence > 99.9% despite retries."),
        NEARLY_FAILED("NEARLY-FAILED", "badge badge-warning", "Regression confidence > 90%, we're going to fail soon."),
        REGRESSED("REGRESSED", "badge badge-danger", "Regression confidence > 99.9% despite retries."),
        IMPROVED("IMPROVED", "badge badge-success", "Improvement confidence > 90%, rebaseline it to keep this improvement! :-)"),
        UNKNOWN("UNKNOWN", "badge badge-dark", "The status is unknown, may be it's cancelled?"),
        FLAKY("FLAKY", "badge badge-danger", "The scenario's difference confidence > 95% even when running identical code."),
        UNTAGGED("UNTAGGED", null, null);

        private String name;
        private String classAttr;
        private String title;

        FixedTag(String name, String classAttr, String title) {
            this.name = name;
            this.classAttr = classAttr;
            this.title = title;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getClassAttr() {
            return classAttr;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getUrl() {
            return null;
        }
    }

    class FlakinessInfoTag implements Tag {
        private static final String FLAKINESS_RATE_TITLE = "Flakiness rate of a scenario is the number of times the scenario had a regression of an improvement with more than 99.9% " +
            " in the flakiness detection builds divided by the total number of runs of the scenario.";
        private static final String FAILURE_THRESHOLD_TITLE = "The failure threshold of flaky scenario, if a flaky scenario performance test's difference is higher than this value, " +
            " it will be recognized as a real failure.";
        private String name;
        private String title;

        static FlakinessInfoTag createFlakinessRateTag(BigDecimal rate) {
            return new FlakinessInfoTag(String.format("FLAKY(%.2f%%)", rate.doubleValue() * 100), FLAKINESS_RATE_TITLE);
        }

        static FlakinessInfoTag createFailureThresholdTag(BigDecimal threshold) {
            return new FlakinessInfoTag(String.format("FAILURE-THRESHOLD(%.2f%%)", threshold.doubleValue() * 100), FAILURE_THRESHOLD_TITLE);
        }

        private FlakinessInfoTag(String name, String title) {
            this.name = name;
            this.title = title;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getClassAttr() {
            return "badge badge-warning";
        }

        @Override
        public String getUrl() {
            return "https://builds.gradle.org/viewType.html?buildTypeId=Gradle_Check_PerformanceFlakinessDetectionCoordinator&tab=buildTypeHistoryList";
        }

        @Override
        public String getTitle() {
            return title;
        }
    }
}
