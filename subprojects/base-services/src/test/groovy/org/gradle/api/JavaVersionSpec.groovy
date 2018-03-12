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


import spock.lang.Specification

class JavaVersionSpec extends Specification {

    private static final BigInteger TOO_BIG = (BigInteger.valueOf(Integer.MAX_VALUE)).add(BigInteger.ONE)
    private static final String TOO_BIG_STR = TOO_BIG.toString()

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
    }

    def convertsStringToVersion() {
        expect:
        JavaVersion.toVersion("1.1") == JavaVersion.VERSION_1_1
        JavaVersion.toVersion("1.3") == JavaVersion.VERSION_1_3
        JavaVersion.toVersion("1.5") == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("1.5.4").major() == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("1.5_4") == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("1.5.0.4_b109").major() == JavaVersion.VERSION_1_5

        JavaVersion.toVersion("5") == JavaVersion.VERSION_1_5
        JavaVersion.toVersion("6") == JavaVersion.VERSION_1_6
        JavaVersion.toVersion("7") == JavaVersion.VERSION_1_7
        JavaVersion.toVersion("8") == JavaVersion.VERSION_1_8
        JavaVersion.toVersion("9") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("10") == JavaVersion.VERSION_1_10

        JavaVersion.toVersion("1.9.0-internal") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("1.9.0-ea") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9-ea") == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9.0.0.15").major() == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9.0.1").major() == JavaVersion.VERSION_1_9
        JavaVersion.toVersion("9.1").major() == JavaVersion.VERSION_1_9

        JavaVersion.toVersion("10.1").major() == JavaVersion.VERSION_1_10
        JavaVersion.toVersion("10.1.2").major() == JavaVersion.VERSION_1_10
        JavaVersion.toVersion("10-ea") == JavaVersion.VERSION_1_10
        JavaVersion.toVersion("10-internal") == JavaVersion.VERSION_1_10
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
    }

    def failsToConvertStringToVersionForInvalidVersion() {
        expect:
        conversionFails("a")
        conversionFails("java-9")
        conversionFails("")
        conversionFails("  ")

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
        JavaVersion version5 = JavaVersion.toVersion('1.5')

        expect:
        version5.java5
        !version5.java6
        !version5.java7
        !version5.java8

        and:
        version5.java5Compatible
        !version5.java6Compatible
        !version5.java7Compatible
        !version5.java8Compatible
    }

    def "uses system property to determine if compatible with Java 6"() {
        JavaVersion version6 = JavaVersion.toVersion('1.6')

        expect:
        !version6.java5
        version6.java6
        !version6.java7
        !version6.java8

        and:
        version6.java5Compatible
        version6.java6Compatible
        !version6.java7Compatible
        !version6.java8Compatible
    }

    def "uses system property to determine if compatible with Java 7"() {
        JavaVersion version7 = JavaVersion.toVersion('1.7')

        expect:
        !version7.java5
        !version7.java6
        version7.java7
        !version7.java8

        and:
        version7.java5Compatible
        version7.java6Compatible
        version7.java7Compatible
        !version7.java8Compatible
    }

    def "uses system property to determine if compatible with Java 8"() {
        JavaVersion version8 = JavaVersion.toVersion('1.8')

        expect:
        !version8.java5
        !version8.java6
        !version8.java7
        version8.java8

        and:
        version8.java5Compatible
        version8.java6Compatible
        version8.java7Compatible
        version8.java8Compatible
    }

    def "uses system property to determine if compatible with Java 9"() {
        JavaVersion version9 = JavaVersion.toVersion(javaVersion)

        expect:
        !version9.java5
        !version9.java6
        !version9.java7
        !version9.java8
        version9.java9

        and:
        version9.java5Compatible
        version9.java6Compatible
        version9.java7Compatible
        version9.java8Compatible
        version9.java9Compatible

        where:
        javaVersion << ['1.9', '9-ea']
    }

    def "uses system property to determine if compatible with Java 10"() {
        JavaVersion version10 = JavaVersion.toVersion(javaVersion)

        expect:
        !version10.java5
        !version10.java6
        !version10.java7
        !version10.java8
        !version10.java9
        version10.java10

        and:
        version10.java5Compatible
        version10.java6Compatible
        version10.java7Compatible
        version10.java8Compatible
        version10.java9Compatible
        version10.java10Compatible

        where:
        javaVersion << ['1.10', '10-ea']
    }

    def 'can recognize multiple version number'() {
        expect:
        JavaVersion.toVersion('9').versions == [9]
        JavaVersion.toVersion('9.1').versions == [9, 1]
        JavaVersion.toVersion('9.0.1').versions == [9, 0, 1]
        JavaVersion.toVersion('9.0.0.1').versions == [9, 0, 0, 1]
        JavaVersion.toVersion('9.0.0.0.1').versions == [9, 0, 0, 0, 1]
        JavaVersion.toVersion('404.1.2').versions == [404, 1, 2]
        JavaVersion.toVersion('9.1.2.3').versions == [9, 1, 2, 3]
        JavaVersion.toVersion('1000.0.0.0.0.0.99999999').versions == [1000, 0, 0, 0, 0, 0, 99999999]
    }

    def 'can recognize version with $pre'() {
        expect:
        JavaVersion.toVersion('9-ea').versions == [9]
        JavaVersion.toVersion('9-internal').versions == [9]
        JavaVersion.toVersion('9-0').versions == [9]
        JavaVersion.toVersion('9.2.7-8').versions == [9, 2, 7]
        JavaVersion.toVersion('2.3.4.5-1a').versions == [2, 3, 4, 5]
    }

    def 'can recognize $build'() {
        expect:
        JavaVersion.toVersion('9+0').versions == [9]
        JavaVersion.toVersion('3.14+9999900').versions == [3, 14]
        JavaVersion.toVersion('9-pre+105').versions == [9]
        JavaVersion.toVersion('6.0.42-8beta+4').versions == [6, 0, 42]
    }

    def 'can recognize version with $opt'() {
        expect:
        JavaVersion.toVersion('9+-foo').versions == [9]
        JavaVersion.toVersion('9-pre-opt').versions == [9]
        JavaVersion.toVersion('42+---bar').versions == [42]
        JavaVersion.toVersion('2.91+-8061493-').versions == [2, 91]
        JavaVersion.toVersion('24+-foo.bar').versions == [24]
        JavaVersion.toVersion('9-ribbit+17-...').versions == [9]
        JavaVersion.toVersion("7+1-$TOO_BIG_STR").versions == [7]
    }

    def 'can compare two versions'() {
        expect:
        JavaVersion.toVersion('9') == JavaVersion.toVersion('9')
        JavaVersion.toVersion('8') < JavaVersion.toVersion('9')
        JavaVersion.toVersion('9') < JavaVersion.toVersion('10')
        JavaVersion.toVersion('9') > JavaVersion.toVersion('8')
        JavaVersion.toVersion('10.512.1') < JavaVersion.toVersion('10.512.2')
        JavaVersion.toVersion('10.512.0.1') < JavaVersion.toVersion('10.512.0.2')
        JavaVersion.toVersion('10.512.0.0.1') < JavaVersion.toVersion('10.512.0.0.2')
        JavaVersion.toVersion('512.10.1') < JavaVersion.toVersion('512.11.1')
        JavaVersion.toVersion('9') == JavaVersion.toVersion('9+-oink')
        JavaVersion.toVersion('9+-ribbit') == JavaVersion.toVersion('9+-moo')
        JavaVersion.toVersion('9-quack+3-ribbit') == JavaVersion.toVersion('9-quack+3-moo')
        JavaVersion.toVersion('9.1+7') == JavaVersion.toVersion('9.1+7-moo-baa-la')
        JavaVersion.toVersion('9.1.1') == JavaVersion.toVersion('9.1.1.0')
        JavaVersion.toVersion('9.1.1.0.0') == JavaVersion.toVersion('9.1.1')
        JavaVersion.toVersion('9.1') == JavaVersion.toVersion('1.9.1')
    }
}
