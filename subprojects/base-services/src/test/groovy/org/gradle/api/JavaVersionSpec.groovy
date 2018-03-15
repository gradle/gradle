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

class JavaVersionSpec extends Specification {
    private static final BigInteger TOO_BIG = (BigInteger.valueOf(Integer.MAX_VALUE)).add(BigInteger.ONE)
    private static final String TOO_BIG_STR = TOO_BIG.toString()

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
        JavaVersion.VERSION_1_10.toString() == "1.10"
        JavaVersion.VERSION_1_11_OR_LATER.toString() == "1.11"
    }

    def convertsStringToVersion() {
        expect:
        JavaVersion.toVersion("1.1") == JavaVersion.VERSION_1_1
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
        JavaVersion.toVersion("10") == JavaVersion.VERSION_1_10

        JavaVersion.toVersion("1.9.0-internal") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("1.9.0-ea") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9-ea") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9.0.0.15") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9.0.1") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9.1") == JavaVersion.VERSION_1_9

        JavaVersion.toVersion("10.1") == JavaVersion.VERSION_1_10
        JavaVersion.toVersion("10.1.2") == JavaVersion.VERSION_1_10
        JavaVersion.toVersion("10-ea") == JavaVersion.VERSION_1_10
        JavaVersion.toVersion("10-internal") == JavaVersion.VERSION_1_10

        JavaVersion.toVersion("11-ea") == JavaVersion.VERSION_1_11_OR_LATER
        JavaVersion.toVersion("12-ea") == JavaVersion.VERSION_1_11_OR_LATER
        JavaVersion.toVersion("999-ea") == JavaVersion.VERSION_1_11_OR_LATER
    }

    def convertClassVersionToJavaVersion() {
        expect:
        JavaVersion.forClassVersion(45) == JavaVersion.VERSION_1_1
        JavaVersion.forClassVersion(46) == JavaVersion.VERSION_1_2
        JavaVersion.forClassVersion(47) == JavaVersion.VERSION_1_3
        JavaVersion.forClassVersion(48) == JavaVersion.VERSION_1_4
        JavaVersion.forClassVersion(49) == JavaVersion.VERSION_1_5
        JavaVersion.forClassVersion(50) == JavaVersion.VERSION_1_6
        JavaVersion.forClassVersion(51) == JavaVersion.VERSION_1_7
        JavaVersion.forClassVersion(52) == JavaVersion.VERSION_1_8
        JavaVersion.forClassVersion(53) == JavaVersion.VERSION_1_9
        JavaVersion.forClassVersion(54) == JavaVersion.VERSION_1_10
        JavaVersion.forClassVersion(55) == JavaVersion.VERSION_1_11_OR_LATER
        JavaVersion.forClassVersion(999) == JavaVersion.VERSION_1_11_OR_LATER
    }

    def failsToConvertStringToVersionForUnknownVersion() {
        expect:
        conversionFails("a")
        conversionFails("java-9")
        conversionFails("")
        conversionFails("  ")

        conversionFails("0.1")
        conversionFails("0.1")

        conversionFails('foo')
        conversionFails('0')
        conversionFails('1.00')
        conversionFails('00')
        conversionFails('09')
        conversionFails(TOO_BIG_STR)
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
        JavaVersion.toVersion(9) == JavaVersion.VERSION_1_9
        JavaVersion.toVersion(10) == JavaVersion.VERSION_1_10
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
            JavaVersion.toVersion(value)
            org.junit.Assert.fail()
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

    def "uses system property to determine if compatible with Java 10"() {
        System.properties['java.version'] = javaVersion

        expect:
        !JavaVersion.current().java5
        !JavaVersion.current().java6
        !JavaVersion.current().java7
        !JavaVersion.current().java8
        !JavaVersion.current().java9
        JavaVersion.current().java10

        and:
        JavaVersion.current().java5Compatible
        JavaVersion.current().java6Compatible
        JavaVersion.current().java7Compatible
        JavaVersion.current().java8Compatible
        JavaVersion.current().java9Compatible
        JavaVersion.current().java10Compatible

        where:
        javaVersion << ['1.10', '10-ea']
    }

    def "uses system property to determine if compatible with Java 11"() {
        System.properties['java.version'] = javaVersion

        expect:
        !JavaVersion.current().java5
        !JavaVersion.current().java6
        !JavaVersion.current().java7
        !JavaVersion.current().java8
        !JavaVersion.current().java9
        !JavaVersion.current().java10

        and:
        JavaVersion.current().java5Compatible
        JavaVersion.current().java6Compatible
        JavaVersion.current().java7Compatible
        JavaVersion.current().java8Compatible
        JavaVersion.current().java9Compatible
        JavaVersion.current().java10Compatible

        where:
        javaVersion << ['1.11', '11-ea', '12-ea', '999']
    }

    /* Following test cases are from http://hg.openjdk.java.net/jdk/jdk/file/af37d9997bd6/test/jdk/java/lang/Runtime/Version/Basic.java */

    def 'can recognize multiple version number'() {
        expect:
        JavaVersion.toVersion('9') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.0.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.0.0.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.0.0.0.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('404.1.2') == JavaVersion.VERSION_1_11_OR_LATER
        JavaVersion.toVersion('9.1.2.3') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('1000.0.0.0.0.0.99999999') == JavaVersion.VERSION_1_11_OR_LATER
    }

    def 'can recognize version with $pre'() {
        expect:
        JavaVersion.toVersion('9-ea') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9-internal') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9-0') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.2.7-8') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('2.3.4.5-1a') == JavaVersion.VERSION_1_2
    }

    def 'can recognize $build'() {
        expect:
        JavaVersion.toVersion('9+0') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('3.14+9999900') == JavaVersion.VERSION_1_3
        JavaVersion.toVersion('9-pre+105') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('6.0.42-8beta+4') == JavaVersion.VERSION_1_6
    }

    def 'can recognize version with $opt'() {
        expect:
        JavaVersion.toVersion('9+-foo') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9-pre-opt') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('42+---bar') == JavaVersion.VERSION_1_11_OR_LATER
        JavaVersion.toVersion('2.91+-8061493-') == JavaVersion.VERSION_1_2
        JavaVersion.toVersion('24+-foo.bar') == JavaVersion.VERSION_1_11_OR_LATER
        JavaVersion.toVersion('9-ribbit+17-...') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("7+1-$TOO_BIG_STR") == JavaVersion.VERSION_1_7
    }
}
