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

import com.google.common.collect.ImmutableList
import org.gradle.language.nativeplatform.internal.Include
import spock.lang.Specification


class DefaultSourceIncludesTest extends Specification {
    List<Include> includes = [ '"quoted1"', "<system1>", '"quoted2"', "macro1", "<system2>", "macro2" ].collect { TestIncludeParser.parse(it, false) }
    DefaultIncludeDirectives sourceIncludes = DefaultIncludeDirectives.of(ImmutableList.copyOf(includes), ImmutableList.of(), ImmutableList.of())

    def "can filter includes" () {
        expect:
        sourceIncludes.quotedIncludes.collect { it.value } == [ "quoted1", "quoted2" ]
        sourceIncludes.systemIncludes.collect { it.value } == [ "system1", "system2" ]
        sourceIncludes.macroIncludes.collect { it.value } == [ "macro1", "macro2" ]
    }

    def "order of includes is preserved" () {
        expect:
        sourceIncludes.all.collect { it.value } == ["quoted1", "system1", "quoted2", "macro1", "system2", "macro2" ]
    }
}
