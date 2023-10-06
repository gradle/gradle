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
package org.gradle.api


import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class JavaVersionSpec extends Specification {
    private static final BigInteger TOO_BIG = (BigInteger.valueOf(Integer.MAX_VALUE)).add(BigInteger.ONE)
    private static final String TOO_BIG_STR = TOO_BIG.toString()

    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

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
        JavaVersion.VERSION_1_9.toString() == "9"
        JavaVersion.VERSION_1_10.toString() == "10"
        JavaVersion.VERSION_11.toString() == "11"
        JavaVersion.VERSION_12.toString() == "12"
        JavaVersion.VERSION_13.toString() == "13"
        JavaVersion.VERSION_14.toString() == "14"
        JavaVersion.VERSION_15.toString() == "15"
        JavaVersion.VERSION_16.toString() == "16"
        JavaVersion.VERSION_17.toString() == "17"
        JavaVersion.VERSION_18.toString() == "18"
        JavaVersion.VERSION_19.toString() == "19"
        JavaVersion.VERSION_20.toString() == "20"
        JavaVersion.VERSION_21.toString() == "21"
        JavaVersion.VERSION_22.toString() == "22"
        JavaVersion.VERSION_23.toString() == "23"
        JavaVersion.VERSION_24.toString() == "24"
        JavaVersion.VERSION_25.toString() == "25"
        JavaVersion.VERSION_HIGHER.toString() == "26"
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

        JavaVersion.toVersion("11-ea") == JavaVersion.VERSION_11
        JavaVersion.toVersion("12-ea") == JavaVersion.VERSION_12
        JavaVersion.toVersion("13-ea") == JavaVersion.VERSION_13
        JavaVersion.toVersion("14-ea") == JavaVersion.VERSION_14
        JavaVersion.toVersion("15-ea") == JavaVersion.VERSION_15
        JavaVersion.toVersion("16-ea") == JavaVersion.VERSION_16
        JavaVersion.toVersion("17-ea") == JavaVersion.VERSION_17
        JavaVersion.toVersion("999-ea") == JavaVersion.VERSION_HIGHER
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
        JavaVersion.forClassVersion(55) == JavaVersion.VERSION_11
        JavaVersion.forClassVersion(56) == JavaVersion.VERSION_12
        JavaVersion.forClassVersion(57) == JavaVersion.VERSION_13
        JavaVersion.forClassVersion(58) == JavaVersion.VERSION_14
        JavaVersion.forClassVersion(59) == JavaVersion.VERSION_15
        JavaVersion.forClassVersion(60) == JavaVersion.VERSION_16
        JavaVersion.forClassVersion(61) == JavaVersion.VERSION_17
        JavaVersion.forClassVersion(62) == JavaVersion.VERSION_18
        JavaVersion.forClassVersion(63) == JavaVersion.VERSION_19
        JavaVersion.forClassVersion(64) == JavaVersion.VERSION_20
        JavaVersion.forClassVersion(65) == JavaVersion.VERSION_21
        JavaVersion.forClassVersion(66) == JavaVersion.VERSION_22
        JavaVersion.forClassVersion(67) == JavaVersion.VERSION_23
        JavaVersion.forClassVersion(68) == JavaVersion.VERSION_24
        JavaVersion.forClassVersion(69) == JavaVersion.VERSION_25
        JavaVersion.forClassVersion(999) == JavaVersion.VERSION_HIGHER
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

    def "uses system property to determine if compatible with Java #versionString"() {
        System.properties['java.version'] = versionString

        expect:
        JavaVersion.current() == current
        JavaVersion.current().java6 == isJava6
        JavaVersion.current().java7 == isJava7
        JavaVersion.current().java8 == isJava8
        JavaVersion.current().java9 == isJava9
        JavaVersion.current().java10 == isJava10
        JavaVersion.current().java11 == isJava11
        JavaVersion.current().java12 == isJava12

        and:
        JavaVersion.current().java6Compatible == isJava6Compatible
        JavaVersion.current().java7Compatible == isJava7Compatible
        JavaVersion.current().java8Compatible == isJava8Compatible
        JavaVersion.current().java9Compatible == isJava9Compatible
        JavaVersion.current().java10Compatible == isJava10Compatible
        JavaVersion.current().java11Compatible == isJava11Compatible
        JavaVersion.current().java12Compatible == isJava12Compatible

        where:
        versionString | current                    | isJava6 | isJava7 | isJava8 | isJava9 | isJava10 | isJava11 | isJava12 | isJava6Compatible | isJava7Compatible | isJava8Compatible | isJava9Compatible | isJava10Compatible | isJava11Compatible | isJava12Compatible
        '1.5'         | JavaVersion.VERSION_1_5    | false   | false   | false   | false   | false    | false    | false    | false             | false             | false             | false             | false              | false              | false
        '1.6'         | JavaVersion.VERSION_1_6    | true    | false   | false   | false   | false    | false    | false    | true              | false             | false             | false             | false              | false              | false
        '1.7'         | JavaVersion.VERSION_1_7    | false   | true    | false   | false   | false    | false    | false    | true              | true              | false             | false             | false              | false              | false
        '1.8'         | JavaVersion.VERSION_1_8    | false   | false   | true    | false   | false    | false    | false    | true              | true              | true              | false             | false              | false              | false
        '1.9'         | JavaVersion.VERSION_1_9    | false   | false   | false   | true    | false    | false    | false    | true              | true              | true              | true              | false              | false              | false
        '9-ea'        | JavaVersion.VERSION_1_9    | false   | false   | false   | true    | false    | false    | false    | true              | true              | true              | true              | false              | false              | false
        '1.10'        | JavaVersion.VERSION_1_10   | false   | false   | false   | false   | true     | false    | false    | true              | true              | true              | true              | true               | false              | false
        '10-ea'       | JavaVersion.VERSION_1_10   | false   | false   | false   | false   | true     | false    | false    | true              | true              | true              | true              | true               | false              | false
        '1.11'        | JavaVersion.VERSION_11     | false   | false   | false   | false   | false    | true     | false    | true              | true              | true              | true              | true               | true               | false
        '11-ea'       | JavaVersion.VERSION_11     | false   | false   | false   | false   | false    | true     | false    | true              | true              | true              | true              | true               | true               | false
        '12-ea'       | JavaVersion.VERSION_12     | false   | false   | false   | false   | false    | false    | true     | true              | true              | true              | true              | true               | true               | true
        '12'          | JavaVersion.VERSION_12     | false   | false   | false   | false   | false    | false    | true     | true              | true              | true              | true              | true               | true               | true
        '999'         | JavaVersion.VERSION_HIGHER | false   | false   | false   | false   | false    | false    | false    | true              | true              | true              | true              | true               | true               | true
    }

    def "isCompatibleWith works as expected"() {
        expect:
        lhVersion.isCompatibleWith(rhVersion) == compatible

        where:
        lhVersion                | rhVersion                | compatible
        JavaVersion.VERSION_1_1  | JavaVersion.VERSION_1_5  | false
        JavaVersion.VERSION_1_5  | JavaVersion.VERSION_1_1  | true

        JavaVersion.VERSION_1_5  | JavaVersion.VERSION_1_10 | false
        JavaVersion.VERSION_1_10 | JavaVersion.VERSION_1_5  | true

        JavaVersion.VERSION_1_10 | JavaVersion.VERSION_13   | false
        JavaVersion.VERSION_13   | JavaVersion.VERSION_1_10 | true
    }

    /* Following test cases are from http://hg.openjdk.java.net/jdk/jdk/file/af37d9997bd6/test/jdk/java/lang/Runtime/Version/Basic.java */

    def 'can recognize multiple version number'() {
        expect:
        JavaVersion.toVersion('9') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.0.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.0.0.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('9.0.0.0.1') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('404.1.2') == JavaVersion.VERSION_HIGHER
        JavaVersion.toVersion('9.1.2.3') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion('1000.0.0.0.0.0.99999999') == JavaVersion.VERSION_HIGHER
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
        JavaVersion.toVersion('42+---bar') == JavaVersion.VERSION_HIGHER
        JavaVersion.toVersion('2.91+-8061493-') == JavaVersion.VERSION_1_2
        JavaVersion.toVersion('999+-foo.bar') == JavaVersion.VERSION_HIGHER
        JavaVersion.toVersion('9-ribbit+17-...') == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("7+1-$TOO_BIG_STR") == JavaVersion.VERSION_1_7
    }
}
