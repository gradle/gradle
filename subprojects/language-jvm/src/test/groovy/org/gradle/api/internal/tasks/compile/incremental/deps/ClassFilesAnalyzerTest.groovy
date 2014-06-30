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

import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.test.*
import org.gradle.internal.classloader.ClasspathUtil
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

@Ignore //TODO SF tighten and refactor the coverage, use mocks
class ClassFilesAnalyzerTest extends Specification {

    @Subject analyzer = new ClassFilesAnalyzer(new DefaultClassDependenciesAnalyzer(), "org.gradle.api.internal.tasks.compile.incremental.test")

    def "knows relevant dependents"() {
        def classesDir = ClasspathUtil.getClasspathForClass(ClassFilesAnalyzerTest)
        def tree = new FileTreeAdapter(new DirectoryFileTree(classesDir))

        when:
        tree.visit(analyzer)
        def a = analyzer.analysis

        then:
        a.getRelevantDependents(SomeClass.name).dependentClasses == [SomeOtherClass.name] as Set
        a.getRelevantDependents(SomeOtherClass.name).dependentClasses == [] as Set
        a.getRelevantDependents(YetAnotherClass.name).dependentClasses == [SomeOtherClass.name] as Set
        a.getRelevantDependents(AccessedFromPrivateClass.name).dependentClasses == [SomeClass.name, SomeOtherClass.name] as Set
        a.getRelevantDependents(HasPrivateConstants.name).dependentClasses == [] as Set
        a.getRelevantDependents(UsedByNonPrivateConstantsClass.name).dependentClasses == [HasNonPrivateConstants.name, HasPrivateConstants.name] as Set
        a.getRelevantDependents(HasNonPrivateConstants.name).dependencyToAll
    }
}
