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


package org.gradle.api.internal.tasks.compile.incremental.analyzer

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.SomeClassAnnotation
import org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.SomeRuntimeAnnotation
import org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.SomeSourceAnnotation
import org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.UsesClassAnnotation
import org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.UsesRuntimeAnnotation
import org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.UsesSourceAnnotation
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis
import org.gradle.api.internal.tasks.compile.incremental.test.AccessedFromPackagePrivateField
import org.gradle.api.internal.tasks.compile.incremental.test.AccessedFromPrivateField
import org.gradle.api.internal.tasks.compile.incremental.test.AccessedFromPrivateMethod
import org.gradle.api.internal.tasks.compile.incremental.test.AccessedFromPrivateMethodBody
import org.gradle.api.internal.tasks.compile.incremental.test.HasInnerClass
import org.gradle.api.internal.tasks.compile.incremental.test.HasNonPrivateConstants
import org.gradle.api.internal.tasks.compile.incremental.test.HasPrivateConstants
import org.gradle.api.internal.tasks.compile.incremental.test.HasPublicConstants
import org.gradle.api.internal.tasks.compile.incremental.test.SomeClass
import org.gradle.api.internal.tasks.compile.incremental.test.SomeOtherClass
import org.gradle.api.internal.tasks.compile.incremental.test.UsedByNonPrivateConstantsClass
import org.gradle.api.internal.tasks.compile.incremental.test.YetAnotherClass
import spock.lang.Specification
import spock.lang.Subject

class DefaultClassDependenciesAnalyzerTest extends Specification {

    @Subject
    analyzer = new DefaultClassDependenciesAnalyzer(new StringInterner())

    private ClassAnalysis analyze(Class foo) {
        analyzer.getClassAnalysis(classStream(foo))
    }

    def "knows the name of a class"() {
        expect:
        analyze(SomeOtherClass).className == SomeOtherClass.name
        analyze(HasInnerClass.InnerThing).className == HasInnerClass.InnerThing.name
    }

    def "knows dependencies of a java class"() {
        when:
        def analysis = analyze(SomeOtherClass)

        then:
        analysis.accessibleClassDependencies == [SomeClass.name] as Set
        analysis.privateClassDependencies == [YetAnotherClass.name] as Set
    }

    def "knows dependencies of a java class complex"() {
        when:
        def analysis = analyze(SomeClass)

        then:
        analysis.accessibleClassDependencies == [AccessedFromPackagePrivateField.name] as Set
        analysis.privateClassDependencies == [AccessedFromPrivateField.name,
                                              AccessedFromPrivateMethod.name,
                                              AccessedFromPrivateMethodBody.name,
                                              // AccessedFromPrivateClass.name, // would be in ClassAnalysis for SomeClass$Foo
                                              // AccessedFromPrivateClassPublicField.name, // would be in ClassAnalysis for SomeClass$Foo
                                              SomeClass.name + '$Foo',
                                              SomeClass.name + '$1'] as Set
    }

    def "knows basic class dependencies of a groovy class"() {
        def deps = analyze(DefaultClassDependenciesAnalyzerTest).accessibleClassDependencies

        expect:
        deps.contains(Specification.class.name)
    }

    def "knows if a class have non-private constants"() {
        when:
        def analysis = analyze(HasNonPrivateConstants)

        then:
        analysis.accessibleClassDependencies == [UsedByNonPrivateConstantsClass.name] as Set
        analysis.privateClassDependencies == [] as Set
        !analysis.dependencyToAllReason
        analysis.constants == ['X|1'.hashCode()] as Set

        when:
        analysis = analyze(HasPublicConstants)

        then:
        analysis.accessibleClassDependencies.isEmpty()
        analysis.privateClassDependencies.isEmpty()
        !analysis.dependencyToAllReason
        analysis.constants == ['X|1'.hashCode()] as Set

        when:
        analysis = analyze(HasPrivateConstants)

        then:
        analysis.accessibleClassDependencies == [] as Set
        analysis.privateClassDependencies == [HasNonPrivateConstants.name] as Set
        !analysis.dependencyToAllReason
        analysis.constants == [] as Set
    }

    def "knows if a class uses annotations with source retention"() {
        expect:
        analyze(UsesRuntimeAnnotation).accessibleClassDependencies  == ["org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.SomeRuntimeAnnotation"] as Set
        analyze(UsesRuntimeAnnotation).privateClassDependencies  == [] as Set
        analyze(SomeRuntimeAnnotation).accessibleClassDependencies.isEmpty()
        analyze(SomeRuntimeAnnotation).privateClassDependencies.isEmpty()
        !analyze(SomeRuntimeAnnotation).dependencyToAllReason

        analyze(UsesClassAnnotation).accessibleClassDependencies == ["org.gradle.api.internal.tasks.compile.incremental.analyzer.annotations.SomeClassAnnotation"] as Set
        analyze(UsesClassAnnotation).privateClassDependencies == [] as Set
        analyze(SomeClassAnnotation).accessibleClassDependencies.isEmpty()
        analyze(SomeClassAnnotation).privateClassDependencies.isEmpty()
        !analyze(SomeClassAnnotation).dependencyToAllReason

        analyze(UsesSourceAnnotation).accessibleClassDependencies.isEmpty() //source annotations are wiped from the bytecode
        analyze(UsesSourceAnnotation).privateClassDependencies.isEmpty() //source annotations are wiped from the bytecode
        analyze(SomeSourceAnnotation).accessibleClassDependencies.isEmpty()
        analyze(SomeSourceAnnotation).privateClassDependencies.isEmpty()
        analyze(SomeSourceAnnotation).dependencyToAllReason
    }

    InputStream classStream(Class aClass) {
        aClass.classLoader.getResourceAsStream(aClass.getName().replace(".", "/") + ".class")
    }
}
