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

package org.gradle.performance.results;

import org.gradle.ci.common.model.FlakyTest;

import static org.gradle.performance.results.Tag.FixedTag.KNOWN_FLAKY;
import static org.gradle.performance.results.Tag.FixedTag.UNTAGGED;

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
        FAILED("FAILED", "badge badge-danger", "Regression confidence > 99% despite retries."),
        NEARLY_FAILED("NEARLY-FAILED", "badge badge-warning", "Regression confidence > 90%, we're going to fail soon."),
        REGRESSED("REGRESSED", "badge badge-danger", "Regression confidence > 99% despite retries."),
        IMPROVED("IMPROVED", "badge badge-success", "Improvement confidence > 90%, rebaseline it to keep this improvement! :-)"),
        UNKNOWN("UNKNOWN", "badge badge-dark", "The status is unknown, may be it's cancelled?"),
        FLAKY("FLAKY", "badge badge-danger", "The scenario's difference confidence > 95% even when running identical code."),
        KNOWN_FLAKY("KNOWN-FLAKY", "badge badge-danger", "The scenario was marked as flaky in gradle-private issue tracker recently."),
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

    class KnownFlakyTag implements Tag {
        private String url;

        public KnownFlakyTag(FlakyTest flakyTest) {
            this.url = flakyTest.getIssue().getHtmlUrl().toString();
        }

        @Override
        public String getName() {
            return KNOWN_FLAKY.name;
        }

        @Override
        public String getClassAttr() {
            return KNOWN_FLAKY.classAttr;
        }

        @Override
        public String getTitle() {
            return KNOWN_FLAKY.title;
        }

        @Override
        public String getUrl() {
            return url;
        }
    }
}
