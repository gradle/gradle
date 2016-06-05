/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api;


import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

public class JavaVersionSpec extends Specification {

    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def setup() {
        JavaVersion.resetCurrent()
    }

    def cleanup() {
        JavaVersion.resetCurrent()
    }

    def toStringReturnsVersion() {
        expect:
        JavaVersion.VERSION_1_3.toString() == "1.3"
        JavaVersion.VERSION_1_4.toString() == "1.4"
        JavaVersion.VERSION_1_5.toString() == "1.5"
        JavaVersion.VERSION_1_6.toString() == "1.6"
        JavaVersion.VERSION_1_7.toString() == "1.7"
        JavaVersion.VERSION_1_8.toString() == "1.8"
        JavaVersion.VERSION_1_9.toString() == "1.9"
    }

    def convertsStringToVersion() {
        expect:
        JavaVersion.toVersion("1.3") == JavaVersion.VERSION_1_3
        JavaVersion.toVersion("1.5") == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("1.5.4") == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("1.5_4") == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("1.5.0.4_b109") == JavaVersion.VERSION_1_5

        JavaVersion.toVersion("5") == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("6") == JavaVersion.VERSION_1_6
        JavaVersion.toVersion("7") == JavaVersion.VERSION_1_7
        JavaVersion.toVersion("8") == JavaVersion.VERSION_1_8
        JavaVersion.toVersion("9") == JavaVersion.VERSION_1_9

        JavaVersion.toVersion("1.9.0-internal") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("1.9.0-ea") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9-ea") == JavaVersion.VERSION_1_9
    }

    def convertClassVersionToJavaVersion() {
        expect:
        JavaVersion.forClassVersion(49) == JavaVersion.VERSION_1_5
        JavaVersion.forClassVersion(50) == JavaVersion.VERSION_1_6
        JavaVersion.forClassVersion(51) == JavaVersion.VERSION_1_7
        JavaVersion.forClassVersion(52) == JavaVersion.VERSION_1_8
        JavaVersion.forClassVersion(53) == JavaVersion.VERSION_1_9
    }

    def failsToConvertStringToVersionForUnknownVersion() {
        expect:
        conversionFails("1");
        conversionFails("2");

        conversionFails("10");
        conversionFails("17");

        conversionFails("a");
        conversionFails("java-9");
        conversionFails("");
        conversionFails("  ");

        conversionFails("0.1");
        conversionFails("1.54");
        conversionFails("1.10");
        conversionFails("2.0");
        conversionFails("1_4");
        conversionFails("8.1");
        conversionFails("9.1");
        conversionFails("9.0.0");
        conversionFails("10.1.2");

        conversionFails("9-");
        conversionFails("10-ea");
    }

    def convertsVersionToVersion() {
        expect:
        JavaVersion.toVersion(JavaVersion.VERSION_1_4) == JavaVersion.VERSION_1_4
    }

    def convertsNumberToVersion() {
        expect:
        JavaVersion.toVersion(1.3) == JavaVersion.VERSION_1_3
        JavaVersion.toVersion(1.5) == JavaVersion.VERSION_1_5
        JavaVersion.toVersion(5) == JavaVersion.VERSION_1_5
        JavaVersion.toVersion(6) == JavaVersion.VERSION_1_6
        JavaVersion.toVersion(7) == JavaVersion.VERSION_1_7
        JavaVersion.toVersion(1.7) == JavaVersion.VERSION_1_7
        JavaVersion.toVersion(1.8) == JavaVersion.VERSION_1_8
        JavaVersion.toVersion(1.9) == JavaVersion.VERSION_1_9
    }

    def failsToConvertNumberToVersionForUnknownVersion() {
        expect:
        conversionFails(1);
        conversionFails(2);
        conversionFails(17);
        conversionFails(1.21);
        conversionFails(2.0);
        conversionFails(4.2);
    }

    def currentReturnsJvmVersion() {
        expect:
        JavaVersion.current() == JavaVersion.toVersion(System.getProperty("java.version"))
    }

    def convertsNullToNull() {
        expect:
        JavaVersion.toVersion(null) == null
    }

    private void conversionFails(Object value) {
        try {
            JavaVersion.toVersion(value);
            org.junit.Assert.fail();
        } catch (IllegalArgumentException e) {
            assert e.getMessage() == "Could not determine java version from '" + value + "'."
        }
    }

    def "uses system property to determine if compatible with Java 5"() {
        System.properties['java.version'] = '1.5'

        expect:
        JavaVersion.current().java5
        !JavaVersion.current().java6
        !JavaVersion.current().java7
        !JavaVersion.current().java8

        and:
        JavaVersion.current().java5Compatible
        !JavaVersion.current().java6Compatible
        !JavaVersion.current().java7Compatible
        !JavaVersion.current().java8Compatible
    }

    def "uses system property to determine if compatible with Java 6"() {
        System.properties['java.version'] = '1.6'

        expect:
        !JavaVersion.current().java5
        JavaVersion.current().java6
        !JavaVersion.current().java7
        !JavaVersion.current().java8

        and:
        JavaVersion.current().java5Compatible
        JavaVersion.current().java6Compatible
        !JavaVersion.current().java7Compatible
        !JavaVersion.current().java8Compatible
    }

    def "uses system property to determine if compatible with Java 7"() {
        System.properties['java.version'] = '1.7'

        expect:
        !JavaVersion.current().java5
        !JavaVersion.current().java6
        JavaVersion.current().java7
        !JavaVersion.current().java8

        and:
        JavaVersion.current().java5Compatible
        JavaVersion.current().java6Compatible
        JavaVersion.current().java7Compatible
        !JavaVersion.current().java8Compatible
    }

    def "uses system property to determine if compatible with Java 8"() {
        System.properties['java.version'] = '1.8'

        expect:
        !JavaVersion.current().java5
        !JavaVersion.current().java6
        !JavaVersion.current().java7
        JavaVersion.current().java8

        and:
        JavaVersion.current().java5Compatible
        JavaVersion.current().java6Compatible
        JavaVersion.current().java7Compatible
        JavaVersion.current().java8Compatible
    }

    def "uses system property to determine if compatible with Java 9"() {
        System.properties['java.version'] = javaVersion

        expect:
        !JavaVersion.current().java5
        !JavaVersion.current().java6
        !JavaVersion.current().java7
        !JavaVersion.current().java8
        JavaVersion.current().java9

        and:
        JavaVersion.current().java5Compatible
        JavaVersion.current().java6Compatible
        JavaVersion.current().java7Compatible
        JavaVersion.current().java8Compatible
        JavaVersion.current().java9Compatible

        where:
        javaVersion << ['1.9', '9-ea']
    }
}
