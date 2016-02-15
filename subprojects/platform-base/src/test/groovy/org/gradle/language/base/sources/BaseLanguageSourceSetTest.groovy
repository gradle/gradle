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

import org.gradle.api.internal.file.TestFiles
import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class BaseLanguageSourceSetTest extends Specification {
    def "has useful display names"() {
        def sourceSet = BaseLanguageSourceSet.create(TestSourceSet, BaseLanguageSourceSet, new DefaultComponentSpecIdentifier("project", "test"), "parent", TestFiles.sourceDirectorySetFactory())

        expect:
        sourceSet.name == "test"
        sourceSet.displayName == "Test source 'parent:test'"
        sourceSet.toString() == sourceSet.displayName
    }

    def "calculates display name from public type name"() {
        expect:
        def sourceSet = BaseLanguageSourceSet.create(publicType, BaseLanguageSourceSet, new DefaultComponentSpecIdentifier("project", "test"), "parent", TestFiles.sourceDirectorySetFactory())
        sourceSet.displayName == displayName

        where:
        publicType                | displayName
        SomeTypeLanguageSourceSet | "SomeType source 'parent:test'"
        SomeTypeSourceSet         | "SomeType source 'parent:test'"
        SomeTypeSource            | "SomeType source 'parent:test'"
        SomeTypeSet               | "SomeType source 'parent:test'"
        SomeType                  | "SomeType source 'parent:test'"
        SomeResourcesSet          | "SomeResources 'parent:test'"
    }

    interface TestSourceSet extends LanguageSourceSet {}

    interface SomeTypeLanguageSourceSet extends LanguageSourceSet {}

    interface SomeTypeSourceSet extends LanguageSourceSet {}

    interface SomeTypeSet extends LanguageSourceSet {}

    interface SomeTypeSource extends LanguageSourceSet {}

    interface SomeType extends LanguageSourceSet {}

    interface SomeResourcesSet extends LanguageSourceSet {}

}
