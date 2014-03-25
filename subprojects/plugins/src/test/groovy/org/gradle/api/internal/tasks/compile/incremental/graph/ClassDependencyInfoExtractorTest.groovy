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

package org.gradle.api.internal.tasks.compile.incremental.graph

import org.gradle.api.internal.tasks.compile.incremental.analyzer.*
import spock.lang.Specification

class ClassDependencyInfoExtractorTest extends Specification {

    def "knows recursive dependency tree"() {
        def info = new ClassDependencyInfoExtractor(new File(ClassDependencyInfoExtractorTest.classLoader.getResource("").toURI())).extractInfo("org.gradle.api.internal.tasks.compile.incremental")
        expect:
        info.getActualDependents(SomeClass.name) == [SomeOtherClass.name] as Set
        info.getActualDependents(SomeOtherClass.name) == [] as Set
        info.getActualDependents(YetAnotherClass.name) == [SomeOtherClass.name] as Set
        info.getActualDependents(AccessedFromPrivateClass.name) == [SomeClass.name, SomeOtherClass.name] as Set
        info.getActualDependents(HasPrivateConstants.name) == [] as Set
        info.getActualDependents(HasNonPrivateConstants.name) == null
        info.getActualDependents(UsedByNonPrivateConstantsClass.name) == [HasNonPrivateConstants.name, HasPrivateConstants.name] as Set
    }

    //TODO SF tighten and refactor the coverage
}
