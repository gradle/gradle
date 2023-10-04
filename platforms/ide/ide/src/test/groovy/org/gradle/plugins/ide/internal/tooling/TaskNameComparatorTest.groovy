/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling

import org.gradle.plugins.ide.internal.tooling.model.TaskNameComparator
import spock.lang.Specification
import spock.lang.Unroll

class TaskNameComparatorTest extends Specification {
    @Unroll('compares task names #first and #second')
    def compare() {
        def comparator = new TaskNameComparator()

        expect:
        comparator.compare(first, second) < 0
        comparator.compare(second, first) > 0
        comparator.compare(first, first) == 0
        comparator.compare(second, second) == 0

        where:
        first | second
        ':t1' | ':t2'
        ':a:t1' | ':a:t2'
        ':a:t1' | ':a1:t1'
        ':t1' | ':a:t2'
        ':b:t2' | ':b:c:t2'
    }
}
