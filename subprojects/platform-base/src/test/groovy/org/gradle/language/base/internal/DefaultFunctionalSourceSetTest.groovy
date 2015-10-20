/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.base.internal

import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.registry.LanguageRegistry
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Specification
import spock.lang.Unroll

class DefaultFunctionalSourceSetTest extends Specification {
    def "has reasonable string representation"() {
        def sourceSet = new DefaultFunctionalSourceSet("main", Stub(Instantiator), Stub(ProjectSourceSet), Mock(LanguageRegistry), null)

        expect:
        sourceSet.toString() == /source set 'main'/
    }

    @Unroll
    def "should add a default source directory to a language source sets"() {
        def functionalSourceSet = new DefaultFunctionalSourceSet("main", Stub(Instantiator), Stub(ProjectSourceSet), Mock(LanguageRegistry), baseDir)
        LanguageSourceSet lss = Mock()
        lss.getSourceDirConvention() >> sourceSetConvention
        lss.getParentName() >> parentName

        expect:
        functionalSourceSet.calculateDefaultPath("lssName", lss) == expectedPath

        where:
        baseDir             | parentName   | sourceSetConvention      | expectedPath
        new TestFile('top') | null         | 'someConvention/somedir' | new TestFile('top/someConvention/somedir/lssName').path
        new TestFile('top') | null         | ''                       | new TestFile('top/lssName').path
        new TestFile('top') | null         | null                     | new TestFile('top/lssName').path
        new TestFile('top') | 'someParent' | 'someConvention/somedir' | new TestFile('top/someConvention/somedir/someParent/lssName').path
        new TestFile('top') | 'someParent' | null                     | new TestFile('top/someParent/lssName').path
        new TestFile('top') | null         | null                     | new TestFile('top/lssName').path
    }

}
