/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems

import org.gradle.api.problems.internal.DefaultProblemCategory
import org.gradle.api.problems.internal.ProblemCategory
import spock.lang.Specification

class ProblemCategoryTest extends Specification {

    def 'can be created with #namespace:#category'() {
        given:
        ProblemCategory pc = DefaultProblemCategory.create(namespace, category, subcategory as String[])

        expect:
        pc.namespace == namespace
        pc.category == category
        pc.subcategories == subcategory
        pc.toString() == expectedToString

        where:
        namespace                 | category        | subcategory       | expectedToString
        'org.gradle'              | 'deprecation'   | []                | 'org.gradle:deprecation'
        'gradle.plugin'           | 'deprecation'   | ['sub']           | 'gradle.plugin:deprecation:sub'
        'org.gradle'              | 'some-category' | ['sub', 'subsub'] | 'org.gradle:some-category:sub:subsub'
    }
}
