/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeType
import spock.lang.Specification


class IncludeWithSimpleExpressionTest extends Specification {
    def "can parse include string" () {
        when:
        Include include = IncludeWithSimpleExpression.parse(value, isImport)

        then:
        include.getValue() == includeValue
        include.getType() == type
        include.isImport() == isImport

        where:
        value      | includeValue | type               | isImport
        '"quoted"' | "quoted"     | IncludeType.QUOTED | true
        '"quoted"' | "quoted"     | IncludeType.QUOTED | false
        "<system>" | "system"     | IncludeType.SYSTEM | true
        "<system>" | "system"     | IncludeType.SYSTEM | false
        "macro"    | "macro"      | IncludeType.MACRO  | true
        "macro"    | "macro"      | IncludeType.MACRO  | false
    }
}
