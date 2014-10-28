/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.util

import spock.lang.Specification

import static org.gradle.internal.util.NumberUtil.percent

class NumberUtilTest extends Specification {

    def "knows percentage"() {
        expect:
        percent(100, 0) == 0
        percent(100, 1) == 1
        percent(100, 99) == 99
        percent(100, 100) == 100
        percent(100, 101) == 101
        percent(200, 50) == 25
        percent(200, 50) == 25
        percent(0, 50) == 0
        percent(301, 17) == 5
    }
}

