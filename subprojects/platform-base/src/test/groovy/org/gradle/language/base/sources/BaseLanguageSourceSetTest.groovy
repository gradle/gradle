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

package org.gradle.language.base.sources

import org.gradle.api.internal.file.FileResolver
import org.gradle.language.base.LanguageSourceSet
import spock.lang.Specification

class BaseLanguageSourceSetTest extends Specification {
    def "has useful display names"() {
        def sourceSet = BaseLanguageSourceSet.create(TestSourceSet, TestSourceSetImpl, "test", "parent", Stub(FileResolver))

        expect:
        sourceSet.name == "test"
        sourceSet.displayName == "TestSourceSet 'parent:test'"
        sourceSet.toString() == sourceSet.displayName
    }

    interface TestSourceSet extends LanguageSourceSet {}

    static class TestSourceSetImpl extends BaseLanguageSourceSet implements TestSourceSet {}
}
