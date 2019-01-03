/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.source
import org.gradle.api.Project
import org.gradle.build.docs.dsl.source.model.ClassMetaData
import org.gradle.build.docs.model.SimpleClassMetaDataRepository
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ExtractDslMetaDataTaskTest extends Specification {
    final Project project = new ProjectBuilder().build()
    final ExtractDslMetaDataTask task = project.tasks.create('dsl', ExtractDslMetaDataTask.class)
    final SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<ClassMetaData>()

    def setup() {
        task.destFile = project.file('meta-data.bin')
    }

    def cleanup() {
        task.destFile?.delete()
    }

    def extractsClassMetaData() {
        task.source testFile('org/gradle/test/JavaClass.java')
        task.source testFile('org/gradle/test/JavaInterface.java')
        task.source testFile('org/gradle/test/A.java')
        task.source testFile('org/gradle/test/CombinedInterface.java')
        task.source testFile('org/gradle/test/Interface1.java')
        task.source testFile('org/gradle/test/Interface2.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClass')
        !javaClass.groovy
        !javaClass.isInterface()
        !javaClass.enum
        javaClass.rawCommentText.contains('This is a java class.')
        javaClass.superClassName == 'org.gradle.test.A'
        javaClass.interfaceNames == ['org.gradle.test.CombinedInterface', 'org.gradle.test.JavaInterface']
        javaClass.annotationTypeNames == []

        def javaInterface = repository.get('org.gradle.test.JavaInterface')
        !javaInterface.groovy
        javaInterface.isInterface()
        !javaInterface.enum
        javaInterface.superClassName == null
        javaInterface.interfaceNames == ['org.gradle.test.Interface1', 'org.gradle.test.Interface2']
        javaInterface.annotationTypeNames == []
    }

    def extractsPropertyMetaData() {
        task.source testFile('org/gradle/test/JavaClass.java')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClass')
        javaClass.declaredPropertyNames == ['readOnly', 'writeOnly', 'someProp', 'flag', 'arrayProp'] as Set

        def readOnly = javaClass.findDeclaredProperty('readOnly')
        readOnly.type.signature == 'java.lang.String'
        readOnly.rawCommentText.contains('A read-only property.')
        readOnly.readable
        !readOnly.writeable
        readOnly.getter.rawCommentText.contains('A read-only property.')
        !readOnly.setter

        def writeOnly = javaClass.findDeclaredProperty('writeOnly')
        writeOnly.type.signature == 'org.gradle.test.JavaInterface'
        writeOnly.rawCommentText.contains('A write-only property.')
        !writeOnly.readable
        writeOnly.writeable
        !writeOnly.getter
        writeOnly.setter.rawCommentText.contains('A write-only property.')

        def someProp = javaClass.findDeclaredProperty('someProp')
        someProp.type.signature == 'org.gradle.test.JavaInterface'
        someProp.rawCommentText.contains('A property.')
        someProp.readable
        someProp.writeable
        someProp.getter.rawCommentText.contains('A property.')
        someProp.setter.rawCommentText.contains('The setter for a property.')

        def flag = javaClass.findDeclaredProperty('flag')
        flag.type.signature == 'boolean'
        flag.rawCommentText.contains('A boolean property.')
        flag.readable
        !flag.writeable
        flag.getter.rawCommentText.contains('A boolean property.')
        !flag.setter

        def arrayProp = javaClass.findDeclaredProperty('arrayProp')
        arrayProp.type.signature == 'org.gradle.test.JavaInterface[][][]'
        arrayProp.rawCommentText.contains('An array property.')
        arrayProp.readable
        !arrayProp.writeable
        arrayProp.getter.rawCommentText.contains('An array property.')
        !arrayProp.setter
    }

    def extractsMethodMetaData() {
        task.source testFile('org/gradle/test/JavaClassWithMethods.java')
        task.source testFile('org/gradle/test/CombinedInterface.java')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithMethods')
        javaClass.declaredMethods.collect { it.name } as Set == ['stringMethod', 'refTypeMethod', 'voidMethod', 'arrayMethod', 'getIntProp', 'setIntProp'] as Set

        def stringMethod = javaClass.declaredMethods.find { it.name == 'stringMethod' }
        stringMethod.rawCommentText.contains('A method that returns String')
        stringMethod.returnType.signature == 'java.lang.String'
        stringMethod.parameters.collect { it.name } == ['stringParam']
        stringMethod.parameters[0].name == 'stringParam'
        stringMethod.parameters[0].type.signature == 'java.lang.String'

        def refTypeMethod = javaClass.declaredMethods.find { it.name == 'refTypeMethod' }
        refTypeMethod.rawCommentText.contains('A method that returns a reference type.')
        refTypeMethod.returnType.signature == 'org.gradle.test.CombinedInterface'
        refTypeMethod.parameters.collect { it.name } == ['refParam', 'aFlag']
        refTypeMethod.parameters[0].name == 'refParam'
        refTypeMethod.parameters[0].type.signature == 'org.gradle.test.JavaInterface'
        refTypeMethod.parameters[1].name == 'aFlag'
        refTypeMethod.parameters[1].type.signature == 'boolean'

        def voidMethod = javaClass.declaredMethods.find { it.name == 'voidMethod' }
        voidMethod.rawCommentText.contains('A method that returns void.')
        voidMethod.returnType.signature == 'void'
        voidMethod.parameters.collect { it.name } == []

        def arrayMethod = javaClass.declaredMethods.find { it.name == 'arrayMethod' }
        arrayMethod.returnType.signature == 'java.lang.String[][]'
        arrayMethod.returnType.arrayDimensions == 2
        arrayMethod.parameters.collect { it.name } == ['strings']
        arrayMethod.parameters[0].name == 'strings'
        arrayMethod.parameters[0].type.signature == 'java.lang.String[]...'
        arrayMethod.parameters[0].type.arrayDimensions == 2
        arrayMethod.parameters[0].type.varargs

        javaClass.declaredPropertyNames == ['intProp'] as Set
    }

    def extractsConstantsFromClassSource() {
        task.source testFile('org/gradle/test/JavaClassWithConstants.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithConstants')
        javaClass.constants.keySet() == ['INT_CONST', 'STRING_CONST', 'OBJECT_CONST', 'CHAR_CONST'] as Set

        javaClass.constants['INT_CONST'] == '9'
        javaClass.constants['STRING_CONST'] == 'some-string'
        javaClass.constants['CHAR_CONST'] == 'a'
        javaClass.constants['OBJECT_CONST'] == null
    }

    def extractsConstantsFromInterfaceSource() {
        task.source testFile('org/gradle/test/JavaInterfaceWithConstants.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaInterface = repository.get('org.gradle.test.JavaInterfaceWithConstants')
        javaInterface.constants.keySet() == ['INT_CONST', 'STRING_CONST'] as Set

        javaInterface.constants['INT_CONST'] == '120'
        javaInterface.constants['STRING_CONST'] == 'some-string'
    }

    def handlesFullyQualifiedNames() {
        task.source testFile('org/gradle/test/JavaClassWithFullyQualifiedNames.java')
        task.source testFile('org/gradle/test/sub/SubJavaInterface.java')
        task.source testFile('org/gradle/test/sub/SubClass.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithFullyQualifiedNames')
        javaClass.superClassName == 'org.gradle.test.sub.SubClass'
        javaClass.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'java.lang.Runnable']
        javaClass.declaredPropertyNames == ['prop'] as Set

        def prop = javaClass.findDeclaredProperty('prop')
        prop.type.signature == 'org.gradle.test.sub.SubJavaInterface'
    }

    def handlesImportedTypes() {
        task.source testFile('org/gradle/test/JavaClassWithImports.java')
        task.source testFile('org/gradle/test/sub/SubJavaInterface.java')
        task.source testFile('org/gradle/test/sub/SubClass.java')
        task.source testFile('org/gradle/test/sub2/Sub2Interface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithImports')
        javaClass.superClassName == 'org.gradle.test.sub.SubClass'
        javaClass.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'org.gradle.test.sub2.Sub2Interface', 'java.io.Closeable']
        javaClass.declaredPropertyNames == [] as Set
    }

    def handlesEnumTypes() {
        task.source testFile('org/gradle/test/JavaEnum.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaEnum = repository.get('org.gradle.test.JavaEnum')
        !javaEnum.groovy
        !javaEnum.isInterface()
        javaEnum.enum

        and:
        javaEnum.getEnumConstant("A") != null
        javaEnum.getEnumConstant("B") != null
        javaEnum.getEnumConstant("C") == null
    }

    def handlesAnnotationTypes() {
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def annotation = repository.get('org.gradle.test.JavaAnnotation')
        !annotation.groovy
        !annotation.isInterface()
    }

    def handlesNestedAndAnonymousTypes() {
        task.source testFile('org/gradle/test/JavaClassWithInnerTypes.java')
        task.source testFile('org/gradle/test/sub2/Sub2Interface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithInnerTypes')
        javaClass.interfaceNames == ['org.gradle.test.sub2.Sub2Interface']
        javaClass.declaredPropertyNames == ['someProp', 'innerClassProp'] as Set

        def someProp = javaClass.findDeclaredProperty('someProp')
        someProp.type.signature == 'org.gradle.test.sub2.Sub2Interface'

        def innerClassProp = javaClass.findDeclaredProperty('innerClassProp')
        innerClassProp.type.signature == 'org.gradle.test.JavaClassWithInnerTypes.InnerClass.AnotherInner'

        def innerEnum = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerEnum')
        innerEnum.rawCommentText.contains('This is an inner enum.')

        def innerClass = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerClass')
        innerClass.rawCommentText.contains('This is an inner class.')
        innerClass.declaredPropertyNames == ['enumProp'] as Set

        def enumProp = innerClass.findDeclaredProperty('enumProp')
        enumProp.type.signature == 'org.gradle.test.JavaClassWithInnerTypes.InnerEnum'

        def anotherInner = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerClass.AnotherInner')
        anotherInner.rawCommentText.contains('This is an inner inner class.')
        anotherInner.declaredPropertyNames == ['outer'] as Set

        def outer = anotherInner.findDeclaredProperty('outer')
        outer.type.signature == 'org.gradle.test.JavaClassWithInnerTypes.InnerClass'
    }

    def handlesParameterizedTypes() {
        task.source testFile('org/gradle/test/JavaClassWithParameterizedTypes.java')
        task.source testFile('org/gradle/test/CombinedInterface.java')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithParameterizedTypes')

        def setProp = javaClass.findDeclaredProperty('setProp')
        setProp.type.signature == 'java.util.Set<org.gradle.test.CombinedInterface>'

        def mapProp = javaClass.findDeclaredProperty('mapProp')
        mapProp.type.signature == 'java.util.Map<org.gradle.test.CombinedInterface, org.gradle.test.JavaClassWithParameterizedTypes>'

        def wildcardProp = javaClass.findDeclaredProperty('wildcardProp')
        wildcardProp.type.signature == 'java.util.List<?>'

        def upperBoundProp = javaClass.findDeclaredProperty('upperBoundProp')
        upperBoundProp.type.signature == 'java.util.List<? extends org.gradle.test.CombinedInterface>'

        def lowerBoundProp = javaClass.findDeclaredProperty('lowerBoundProp')
        lowerBoundProp.type.signature == 'java.util.List<? super org.gradle.test.CombinedInterface>'

        def nestedProp = javaClass.findDeclaredProperty('nestedProp')
        nestedProp.type.signature == 'java.util.List<? super java.util.Set<? extends java.util.Map<?, org.gradle.test.CombinedInterface[]>>>[]'

        def paramMethod = javaClass.declaredMethods.find { it.name == 'paramMethod' }
        paramMethod.returnType.signature == 'T'
        paramMethod.parameters[0].type.signature == 'T'
    }

    def extractsClassAnnotations() {
        task.source testFile('org/gradle/test/JavaClassWithAnnotation.java')
        task.source testFile('org/gradle/test/JavaInterfaceWithAnnotation.java')
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithAnnotation')
        javaClass.annotationTypeNames == ['java.lang.Deprecated', 'org.gradle.test.JavaAnnotation']

        def javaInterface = repository.get('org.gradle.test.JavaInterfaceWithAnnotation')
        javaInterface.annotationTypeNames == ['java.lang.Deprecated', 'org.gradle.test.JavaAnnotation']
    }

    def extractsMethodAnnotations() {
        task.source testFile('org/gradle/test/JavaClassWithAnnotation.java')
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithAnnotation')
        def method = javaClass.declaredMethods.find { it.name == 'annotatedMethod' }
        method.annotationTypeNames == ['java.lang.Deprecated', 'org.gradle.test.JavaAnnotation']
    }

    def extractsPropertyAnnotations() {
        task.source testFile('org/gradle/test/JavaClassWithAnnotation.java')
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithAnnotation')
        def property = javaClass.declaredProperties.find { it.name == 'annotatedProperty' }
        property.annotationTypeNames == ['java.lang.Deprecated', 'org.gradle.test.JavaAnnotation']
    }

    def "supports Java 8 source code"() {
        task.source testFile('org/gradle/test/Java8Interface.java')
        task.source testFile('org/gradle/test/CombinedInterface.java')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.Java8Interface')
        javaClass.interfaceNames == ['org.gradle.test.CombinedInterface', 'org.gradle.test.JavaInterface']
        javaClass.declaredProperties.collect { it.name } == ['name']
        def property = javaClass.declaredProperties.find { it.name == 'name' }
        property.type.signature == 'java.lang.String'
    }

    def "ignores static imports"() {
        task.source testFile('org/gradle/test/JavaClassWithStaticImport.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithStaticImport')
        javaClass.imports == ['java.util.List']
    }

    def testFile(String fileName) {
        URL resource = getClass().classLoader.getResource(fileName)
        assert resource != null: "Could not find resource '$fileName'."
        assert resource.protocol == 'file'
        return new File(resource.toURI())
    }
}
