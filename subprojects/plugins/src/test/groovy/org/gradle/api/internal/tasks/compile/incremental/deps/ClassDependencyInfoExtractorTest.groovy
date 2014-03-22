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



package org.gradle.api.internal.tasks.compile.incremental.deps

import org.gradle.api.internal.tasks.compile.incremental.analyzer.*
import spock.lang.Specification
import spock.lang.Subject

class ClassDependencyInfoExtractorTest extends Specification {

    @Subject ClassDependencyInfoExtractor extractor = new ClassDependencyInfoExtractor(new ClassDependenciesAnalyzer())

    def "knows relevant dependents"() {
        def classesDir = new File(ClassDependencyInfoExtractorTest.classLoader.getResource("").toURI())
        def info = extractor.extractInfo(classesDir, "org.gradle.api.internal.tasks.compile.incremental")

        expect:
        info.getRelevantDependents(SomeClass.name).dependentClasses == [SomeOtherClass.name] as Set
        info.getRelevantDependents(SomeOtherClass.name).dependentClasses == [] as Set
        info.getRelevantDependents(YetAnotherClass.name).dependentClasses == [SomeOtherClass.name] as Set
        info.getRelevantDependents(AccessedFromPrivateClass.name).dependentClasses == [] as Set
        info.getRelevantDependents(HasPrivateConstants.name).dependentClasses == [] as Set
        info.getRelevantDependents(UsedByNonPrivateConstantsClass.name).dependentClasses == [HasNonPrivateConstants.name] as Set

        info.getRelevantDependents(HasNonPrivateConstants.name).dependencyToAll
        info.getRelevantDependents(HasNonPrivateConstants.name).dependentClasses == null
    }

    //TODO SF tighten and refactor the coverage
}
