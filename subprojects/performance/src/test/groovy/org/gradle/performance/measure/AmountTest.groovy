/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.performance.measure

import spock.lang.Specification

class AmountTest extends Specification {

    def "toString() retains original measurement"() {
        expect:
        Amount.valueOf(value, units).toString() == str

        where:
        value    | units            | str
        0        | Fruit.apples     | "0 apples"
        1        | Fruit.apples     | "1 apple"
        1000     | Fruit.apples     | "1000 apples"
        0.123    | Fruit.apples     | "0.123 apples"
        0.333333 | Fruit.apples     | "0.333333 apples"
        0.5555   | Fruit.apples     | "0.5555 apples"
        -12      | Fruit.apples     | "-12 apples"
        145      | Fruit.oranges    | "145 oranges"
        0.23     | Fruit.grapefruit | "0.23 grapefruit"
    }

    def "format() displays amount in highest possible units with rounded value"() {
        expect:
        Amount.valueOf(value, units).format() == str

        where:
        value    | units         | str
        0        | Fruit.apples  | "0 apples"
        1        | Fruit.apples  | "1 apple"
        0.032    | Fruit.apples  | "0.032 apples"
        2.367722 | Fruit.apples  | "2.368 apples"
        3        | Fruit.apples  | "1 orange"
        5        | Fruit.apples  | "1 grapefruit"
        4        | Fruit.apples  | "1.333 oranges"
        1000     | Fruit.oranges | "600 grapefruit"
        42       | Fruit.oranges | "25.2 grapefruit"
        0.45     | Fruit.oranges | "1.35 apples"
        -12      | Fruit.apples  | "-2.4 grapefruit"
    }

    def "can convert to specific units"() {
        expect:
        Amount.valueOf(value, fromUnits).toUnits(toUnits) == Amount.valueOf(converted, toUnits)
        Amount.valueOf(value, fromUnits).toUnits(toUnits).toString() == Amount.valueOf(converted, toUnits).toString()

        where:
        value | fromUnits        | toUnits          | converted
        21    | Fruit.apples     | Fruit.apples     | 21
        2.1   | Fruit.oranges    | Fruit.apples     | 6.3
        1024  | Fruit.grapefruit | Fruit.apples     | 1024 * 5
        1     | Fruit.apples     | Fruit.grapefruit | 0.2
        1     | Fruit.apples     | Fruit.oranges    | 0.333333
    }

    def "amounts are equal when normalised values are the same"() {
        expect:
        def a = Amount.valueOf(valueA, unitsA)
        def b = Amount.valueOf(valueB, unitsB)
        a.equals(b)
        b.equals(a)
        a.hashCode() == b.hashCode()
        a.compareTo(b) == 0
        b.compareTo(a) == 0

        where:
        valueA  | unitsA        | valueB  | unitsB
        0       | Fruit.apples  | 0       | Fruit.apples
        0       | Fruit.apples  | 0       | Fruit.oranges
        9       | Fruit.apples  | 3       | Fruit.oranges
        5       | Fruit.oranges | 3       | Fruit.grapefruit
        6.3399  | Fruit.apples  | 2.1133  | Fruit.oranges
        0.333   | Fruit.apples  | 0.333   | Fruit.apples
        0.55667 | Fruit.apples  | 0.55667 | Fruit.apples
    }

    def "amounts are not equal when normalised values are different"() {
        expect:
        def a = Amount.valueOf(valueA, unitsA)
        def b = Amount.valueOf(valueB, unitsB)
        !a.equals(b)
        !b.equals(a)
        a.hashCode() != b.hashCode()
        a.compareTo(b) != 0
        b.compareTo(a) != 0

        where:
        valueA | unitsA           | valueB  | unitsB
        0      | Fruit.apples     | 1       | Fruit.apples
        0.334  | Fruit.apples     | 0.333   | Fruit.apples
        0.334  | Fruit.apples     | 0.33433 | Fruit.apples
        1      | Fruit.apples     | 1       | Fruit.oranges
        1      | Fruit.grapefruit | 1       | Fruit.oranges
    }

    def "can compare values"() {
        expect:
        def a = Amount.valueOf(valueA, unitsA)
        def b = Amount.valueOf(valueB, unitsB)
        a < b
        b > a
        a != b
        b != a

        where:
        valueA  | unitsA        | valueB | unitsB
        0.1     | Fruit.oranges | 0.101  | Fruit.oranges
        0.33333 | Fruit.oranges | 1      | Fruit.apples
        1       | Fruit.apples  | 1      | Fruit.oranges
        1       | Fruit.oranges | 1      | Fruit.grapefruit
        -1      | Fruit.apples  | 0      | Fruit.oranges
        5.9     | Fruit.apples  | 2      | Fruit.oranges
    }

    def "can add amounts with same units"() {
        expect:
        Amount.valueOf(a, units) + Amount.valueOf(b, units) == Amount.valueOf(c, units)

        where:
        a    | b     | c      | units
        0    | 0     | 0      | Fruit.apples
        1    | 2     | 3      | Fruit.apples
        1    | 2     | 3      | Fruit.oranges
        23.4 | 0.567 | 23.967 | Fruit.apples
    }

    def "can add amounts with different units of same quantity"() {
        def sum = Amount.valueOf(valueA, unitsA) + Amount.valueOf(valueB, unitsB)
        expect:
        sum == Amount.valueOf(valueC, unitsC)
        sum.toString() == Amount.valueOf(valueC, unitsC).toString()

        where:
        valueA | unitsA        | valueB | unitsB        | valueC  | unitsC
        0      | Fruit.apples  | 0      | Fruit.oranges | 0       | Fruit.apples
        0      | Fruit.apples  | 12     | Fruit.oranges | 12      | Fruit.oranges
        0.7    | Fruit.apples  | 0      | Fruit.oranges | 0.7     | Fruit.apples
        1      | Fruit.apples  | 2      | Fruit.oranges | 7       | Fruit.apples
        3      | Fruit.oranges | 4      | Fruit.oranges | 7       | Fruit.oranges
        12.45  | Fruit.apples  | 0.2222 | Fruit.oranges | 13.1166 | Fruit.apples
    }

    def "can subtract amounts with same units"() {
        expect:
        Amount.valueOf(a, units) - Amount.valueOf(b, units) == Amount.valueOf(c, units)

        where:
        a    | b     | c      | units
        0    | 0     | 0      | Fruit.apples
        2    | 1     | 1      | Fruit.apples
        123  | 12    | 111    | Fruit.oranges
        10   | 56    | -46    | Fruit.apples
        23.4 | 0.567 | 22.833 | Fruit.apples
    }

    def "can subtract amounts with different units of same quantity"() {
        def difference = Amount.valueOf(valueA, unitsA) - Amount.valueOf(valueB, unitsB)
        expect:
        difference == Amount.valueOf(valueC, unitsC)
        difference.toString() == Amount.valueOf(valueC, unitsC).toString()

        where:
        valueA | unitsA        | valueB | unitsB        | valueC  | unitsC
        0      | Fruit.apples  | 0      | Fruit.oranges | 0       | Fruit.apples
        1      | Fruit.oranges | 0      | Fruit.apples  | 1       | Fruit.oranges
        4      | Fruit.apples  | 1      | Fruit.oranges | 1       | Fruit.apples
        2      | Fruit.apples  | 1      | Fruit.oranges | -1      | Fruit.apples
        0      | Fruit.apples  | 12     | Fruit.oranges | -36     | Fruit.apples
        0.7    | Fruit.apples  | 0      | Fruit.oranges | 0.7     | Fruit.apples
        0.7    | Fruit.oranges | 1      | Fruit.apples  | 1.1     | Fruit.apples
        12.45  | Fruit.apples  | 0.2222 | Fruit.oranges | 11.7834 | Fruit.apples
    }

    def "can divide amount by a unitless value"() {
        expect:
        Amount.valueOf(a, Fruit.apples) / b == Amount.valueOf(c, Fruit.apples)

        where:
        a  | b   | c
        0  | 200 | 0
        2  | 1   | 2
        5  | 10  | 0.5
        10 | -2  | -5
        1  | 3   | 0.333333
        2  | 3   | 0.666667
    }

    def "can divide amount by another amount of same quantity"() {
        expect:
        Amount.valueOf(valueA, unitsA) / Amount.valueOf(valueB, unitsB) == result

        where:
        valueA | unitsA        | valueB | unitsB           | result
        0      | Fruit.apples  | 200    | Fruit.apples     | 0
        2      | Fruit.apples  | 1      | Fruit.apples     | 2
        5      | Fruit.apples  | 10     | Fruit.apples     | 0.5
        10     | Fruit.apples  | -2     | Fruit.apples     | -5
        1      | Fruit.apples  | 3      | Fruit.apples     | 0.333333
        2      | Fruit.apples  | 3      | Fruit.apples     | 0.666667
        4      | Fruit.apples  | 1.2    | Fruit.oranges    | 1.111111
        125    | Fruit.oranges | 23.4   | Fruit.grapefruit | 3.205128
    }

    def "can convert to absolute value"() {
        expect:
        Amount.valueOf(12, Fruit.grapefruit).abs() == Amount.valueOf(12, Fruit.grapefruit)
        Amount.valueOf(-34, Fruit.grapefruit).abs() == Amount.valueOf(34, Fruit.grapefruit)
    }

    public static class Fruit {
        static def apples = Units.base(Fruit.class, "apple", "apples")
        static def oranges = apples.times(3, "orange", "oranges")
        static def grapefruit = apples.times(5, "grapefruit")
    }
}
