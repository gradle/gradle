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

import org.gradle.api.internal.tasks.compile.incremental.analyzer.AccessedFromPrivateClass
import org.gradle.api.internal.tasks.compile.incremental.analyzer.HasNonPrivateConstants
import org.gradle.api.internal.tasks.compile.incremental.analyzer.HasPrivateConstants
import org.gradle.api.internal.tasks.compile.incremental.analyzer.SomeClass
import org.gradle.api.internal.tasks.compile.incremental.analyzer.SomeOtherClass
import org.gradle.api.internal.tasks.compile.incremental.analyzer.UsedByNonPrivateConstantsClass
import org.gradle.api.internal.tasks.compile.incremental.analyzer.YetAnotherClass
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/16/14
 */
class ClassDependencyInfoTest extends Specification {

    def "knows recursive dependency tree"() {
        def tree = new ClassDependencyInfo(new File(ClassDependencyInfoTest.classLoader.getResource("").toURI()), "org.gradle.api.internal.tasks.compile.incremental")
        expect:
        tree.getActualDependents(SomeClass.name) == [SomeOtherClass.name] as Set
        tree.getActualDependents(SomeOtherClass.name) == [] as Set
        tree.getActualDependents(YetAnotherClass.name) == [SomeOtherClass.name] as Set
        tree.getActualDependents(AccessedFromPrivateClass.name) == [SomeClass.name, SomeOtherClass.name] as Set
        tree.getActualDependents(HasPrivateConstants.name) == [] as Set
        tree.getActualDependents(HasNonPrivateConstants.name) == null
        tree.getActualDependents(UsedByNonPrivateConstantsClass.name) == null
    }
}
