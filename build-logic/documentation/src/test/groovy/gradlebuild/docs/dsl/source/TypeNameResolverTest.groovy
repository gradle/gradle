/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.source

import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.model.ClassMetaDataRepository
import spock.lang.Specification
import gradlebuild.docs.dsl.source.model.TypeMetaData

class TypeNameResolverTest extends Specification {
    final ClassMetaDataRepository<ClassMetaData> metaDataRepository = Mock()
    final ClassMetaData classMetaData = Mock()
    final TypeNameResolver typeNameResolver = new TypeNameResolver(metaDataRepository)

    def resolvesFullyQualifiedClassName() {
        when:
        def name = typeNameResolver.resolve('org.gradle.SomeClass', classMetaData)

        then:
        name == 'org.gradle.SomeClass'
        _ * classMetaData.innerClassNames >> []
    }

    def resolvesUnqualifiedNameToClassInSamePackage() {
        when:
        def name = typeNameResolver.resolve('SomeClass', classMetaData)

        then:
        name == 'org.gradle.SomeClass'
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.imports >> []
        _ * classMetaData.packageName >> 'org.gradle'
        _ * metaDataRepository.find('org.gradle.SomeClass') >> classMetaData
    }

    def resolvesUnqualifiedNameToImportedClass() {
        when:
        def name = typeNameResolver.resolve('SomeClass', classMetaData)

        then:
        name == 'org.gradle.SomeClass'
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.imports >> ['org.gradle.SomeClass']
    }

    def resolvesUnqualifiedNameToImportedPackage() {
        when:
        def name = typeNameResolver.resolve('SomeClass', classMetaData)

        then:
        name == 'org.gradle.SomeClass'
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.imports >> ['org.gradle.*']
        _ * metaDataRepository.find('org.gradle.SomeClass') >> classMetaData
    }

    def resolvesUnqualifiedNameToInnerClass() {
        when:
        def name = typeNameResolver.resolve('Inner', classMetaData)

        then:
        name == 'org.gradle.SomeClass.Inner'
        _ * classMetaData.innerClassNames >> ['org.gradle.SomeClass.Inner']
        _ * classMetaData.className >> 'org.gradle.SomeClass'
    }

    def resolvesQualifiedNameToInnerClass() {
        ClassMetaData innerClass = Mock()

        when:
        def name = typeNameResolver.resolve('A.B', classMetaData)

        then:
        name == 'org.gradle.SomeClass.A.B'
        _ * classMetaData.innerClassNames >> ['org.gradle.SomeClass.A']
        _ * classMetaData.className >> 'org.gradle.SomeClass'
        _ * metaDataRepository.get('org.gradle.SomeClass.A') >> innerClass
        _ * innerClass.innerClassNames >> ['org.gradle.SomeClass.A.B']
        _ * innerClass.className >> 'org.gradle.SomeClass.A'
    }

    def resolvesUnqualifiedNameToOuterClass() {
        when:
        def name = typeNameResolver.resolve('Outer', classMetaData)

        then:
        name == 'org.gradle.SomeClass.Outer'
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.outerClassName >> 'org.gradle.SomeClass.Outer'
    }

    def resolvesUnqualifiedNameToSiblingClass() {
        ClassMetaData outerClass = Mock()

        when:
        def name = typeNameResolver.resolve('Sibling', classMetaData)

        then:
        name == 'org.gradle.SomeClass.Outer.Sibling'
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.outerClassName >> 'org.gradle.SomeClass.Outer'
        _ * metaDataRepository.get('org.gradle.SomeClass.Outer') >> outerClass
        _ * outerClass.innerClassNames >> ['org.gradle.SomeClass.Outer.Sibling']
    }

    def resolvesUnqualifiedNameToJavaLangPackage() {
        when:
        def name = typeNameResolver.resolve('String', classMetaData)

        then:
        name == 'java.lang.String'
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.imports >> []
    }

    def resolvesUnqualifiedNameToDefaultPackagesAndClassesInGroovySource() {
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.imports >> []
        _ * classMetaData.groovy >> true

        expect:
        typeNameResolver.resolve('Set', classMetaData) == 'java.util.Set'
        typeNameResolver.resolve('File', classMetaData) == 'java.io.File'
        typeNameResolver.resolve('Closure', classMetaData) == 'groovy.lang.Closure'
        typeNameResolver.resolve('BigDecimal', classMetaData) == 'java.math.BigDecimal'
        typeNameResolver.resolve('BigInteger', classMetaData) == 'java.math.BigInteger'
    }

    def resolvesUnqualifiedNameToImportedJavaPackage() {
        when:
        def name = typeNameResolver.resolve('Set', classMetaData)

        then:
        name == 'java.util.Set'
        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.imports >> ['java.util.*']
    }

    def resolvesPrimitiveType() {
        when:
        def name = typeNameResolver.resolve('boolean', classMetaData)

        then:
        name == 'boolean'
    }

    def resolvesParameterisedTypes() {
        def typeMetaData = type('SomeClass')
        typeMetaData.addTypeArg(type('String'))

        when:
        typeNameResolver.resolve(typeMetaData, classMetaData)

        then:
        typeMetaData.signature == 'org.gradle.SomeClass<java.lang.String>'

        _ * classMetaData.innerClassNames >> []
        _ * classMetaData.imports >> ['org.gradle.SomeClass']
    }

    def type(String name) {
        return new TypeMetaData(name)
    }
}

