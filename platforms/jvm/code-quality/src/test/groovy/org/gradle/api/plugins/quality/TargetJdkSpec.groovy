/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.plugins.quality

import spock.lang.Specification

class TargetJdkSpec extends Specification {

    def toStringReturnsVersion() {
        expect:
        TargetJdk.VERSION_1_3.toString() == "1.3"
        TargetJdk.VERSION_1_4.toString() == "1.4"
        TargetJdk.VERSION_1_5.toString() == "1.5"
        TargetJdk.VERSION_1_6.toString() == "1.6"
        TargetJdk.VERSION_1_7.toString() == "1.7"
        TargetJdk.VERSION_JSP.toString() == "jsp"
    }

    def convertsStringToVersion() {
        expect:
        TargetJdk.toVersion("1.3") == TargetJdk.VERSION_1_3
        TargetJdk.toVersion("1.4") == TargetJdk.VERSION_1_4
        TargetJdk.toVersion("1.5") == TargetJdk.VERSION_1_5
        TargetJdk.toVersion("1.6") == TargetJdk.VERSION_1_6
        TargetJdk.toVersion("1.7") == TargetJdk.VERSION_1_7
        TargetJdk.toVersion("jsp") == TargetJdk.VERSION_JSP
        TargetJdk.toVersion("JSP") == TargetJdk.VERSION_JSP
    }

    def failsToConvertStringToVersionForUnknownVersion() {
        expect:
        conversionFails("1.1");
        conversionFails("1.2");
        conversionFails("1");
        conversionFails("2");

        conversionFails("17");

        conversionFails("a");
        conversionFails("");
        conversionFails("  ");

        conversionFails("1.54");
        conversionFails("1.9");
        conversionFails("1.10");
        conversionFails("2.0");
        conversionFails("1_4");
    }

    def convertsVersionToVersion() {
        expect:
        TargetJdk.toVersion(TargetJdk.VERSION_1_4) == TargetJdk.VERSION_1_4
    }

    def convertsNumberToVersion() {
        expect:
        TargetJdk.toVersion(1.3) == TargetJdk.VERSION_1_3
        TargetJdk.toVersion(1.4) == TargetJdk.VERSION_1_4
        TargetJdk.toVersion(1.5) == TargetJdk.VERSION_1_5
        TargetJdk.toVersion(1.6) == TargetJdk.VERSION_1_6
        TargetJdk.toVersion(1.7) == TargetJdk.VERSION_1_7
    }

    def failsToConvertNumberToVersionForUnknownVersion() {
        expect:
        conversionFails(1.1);
        conversionFails(1.2);
        conversionFails(1);
        conversionFails(2);
        conversionFails(17);
        conversionFails(1.21);
        conversionFails(2.0);
        conversionFails(4.2);
    }

    def convertsNullToNull() {
        expect:
        TargetJdk.toVersion(null) == null
    }

    private void conversionFails(Object value) {
        try {
            TargetJdk.toVersion(value);
            org.junit.Assert.fail();
        } catch (IllegalArgumentException e) {
            assert e.getMessage() == "Could not determine targetjdk from '" + value + "'."
        }
    }
}
