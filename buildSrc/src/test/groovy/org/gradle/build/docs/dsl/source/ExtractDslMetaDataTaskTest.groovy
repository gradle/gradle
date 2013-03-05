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
    final ExtractDslMetaDataTask task = project.tasks.add('dsl', ExtractDslMetaDataTask.class)
    final SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<ClassMetaData>()

    def setup() {
        task.destFile = project.file('meta-data.bin')
    }

    def extractsClassMetaDataFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClass.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/A.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')
        task.source testFile('org/gradle/test/Interface1.java')
        task.source testFile('org/gradle/test/Interface2.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClass')
        groovyClass.groovy
        !groovyClass.isInterface()
        !groovyClass.enum
        groovyClass.rawCommentText.contains('This is a groovy class.')
        groovyClass.superClassName == 'org.gradle.test.A'
        groovyClass.interfaceNames == ['org.gradle.test.GroovyInterface', 'org.gradle.test.JavaInterface']
        groovyClass.annotationTypeNames == []

        def groovyInterface = repository.get('org.gradle.test.GroovyInterface')
        groovyInterface.groovy
        groovyInterface.isInterface()
        !groovyInterface.enum
        groovyInterface.superClassName == null
        groovyInterface.interfaceNames == ['org.gradle.test.Interface1', 'org.gradle.test.Interface2']
        groovyInterface.annotationTypeNames == []
    }

    def extractsClassMetaDataFromJavaSource() {
        task.source testFile('org/gradle/test/JavaClass.java')
        task.source testFile('org/gradle/test/JavaInterface.java')
        task.source testFile('org/gradle/test/A.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/Interface1.java')
        task.source testFile('org/gradle/test/Interface2.groovy')

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
        javaClass.interfaceNames == ['org.gradle.test.GroovyInterface', 'org.gradle.test.JavaInterface']
        javaClass.annotationTypeNames == []

        def javaInterface = repository.get('org.gradle.test.JavaInterface')
        !javaInterface.groovy
        javaInterface.isInterface()
        !javaInterface.enum
        javaInterface.superClassName == null
        javaInterface.interfaceNames == ['org.gradle.test.Interface1', 'org.gradle.test.Interface2']
        javaInterface.annotationTypeNames == []
    }

    def extractsPropertyMetaDataFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClass.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClass')
        groovyClass.declaredPropertyNames == ['readOnly', 'writeOnly', 'someProp', 'groovyProp', 'readOnlyGroovyProp', 'arrayProp'] as Set

        def readOnly = groovyClass.findDeclaredProperty('readOnly')
        readOnly.type.signature == 'java.lang.Object'
        readOnly.rawCommentText.contains('A read-only property.')
        !readOnly.writeable
        readOnly.getter.rawCommentText.contains('A read-only property.')
        !readOnly.setter

        def writeOnly = groovyClass.findDeclaredProperty('writeOnly')
        writeOnly.type.signature == 'org.gradle.test.JavaInterface'
        writeOnly.rawCommentText.contains('A write-only property.')
        writeOnly.writeable
        !writeOnly.getter
        writeOnly.setter.rawCommentText.contains('A write-only property.')

        def someProp = groovyClass.findDeclaredProperty('someProp')
        someProp.type.signature == 'org.gradle.test.GroovyInterface'
        someProp.rawCommentText.contains('A property.')
        someProp.writeable
        someProp.getter.rawCommentText.contains('A property.')
        someProp.setter.rawCommentText == ''

        def groovyProp = groovyClass.findDeclaredProperty('groovyProp')
        groovyProp.type.signature == 'org.gradle.test.GroovyInterface'
        groovyProp.rawCommentText.contains('A groovy property.')
        groovyProp.writeable
        groovyProp.getter.rawCommentText == ''
        groovyProp.setter.rawCommentText == ''

        def readOnlyGroovyProp = groovyClass.findDeclaredProperty('readOnlyGroovyProp')
        readOnlyGroovyProp.type.signature == 'java.lang.String'
        readOnlyGroovyProp.rawCommentText.contains('A read-only groovy property.')
        !readOnlyGroovyProp.writeable
        readOnlyGroovyProp.getter.rawCommentText == ''
        !readOnlyGroovyProp.setter

        def arrayProp = groovyClass.findDeclaredProperty('arrayProp')
        arrayProp.type.signature == 'java.lang.String[]'
        arrayProp.rawCommentText.contains('An array property.')
        arrayProp.writeable
        arrayProp.getter.rawCommentText == ''
        arrayProp.setter.rawCommentText == ''
    }

    def extractsPropertyMetaDataFromJavaSource() {
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
        !readOnly.writeable
        readOnly.getter.rawCommentText.contains('A read-only property.')
        !readOnly.setter

        def writeOnly = javaClass.findDeclaredProperty('writeOnly')
        writeOnly.type.signature == 'org.gradle.test.JavaInterface'
        writeOnly.rawCommentText.contains('A write-only property.')
        writeOnly.writeable
        !writeOnly.getter
        writeOnly.setter.rawCommentText.contains('A write-only property.')

        def someProp = javaClass.findDeclaredProperty('someProp')
        someProp.type.signature == 'org.gradle.test.JavaInterface'
        someProp.rawCommentText.contains('A property.')
        someProp.writeable
        someProp.getter.rawCommentText.contains('A property.')
        someProp.setter.rawCommentText.contains('The setter for a property.')

        def flag = javaClass.findDeclaredProperty('flag')
        flag.type.signature == 'boolean'
        flag.rawCommentText.contains('A boolean property.')
        !flag.writeable
        flag.getter.rawCommentText.contains('A boolean property.')
        !flag.setter

        def arrayProp = javaClass.findDeclaredProperty('arrayProp')
        arrayProp.type.signature == 'org.gradle.test.JavaInterface[][][]'
        arrayProp.rawCommentText.contains('An array property.')
        !arrayProp.writeable
        arrayProp.getter.rawCommentText.contains('An array property.')
        !arrayProp.setter
    }

    def extractsMethodMetaDataFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithMethods.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithMethods')
        groovyClass.declaredMethods.collect { it.name } as Set == ['stringMethod', 'refTypeMethod', 'defMethod', 'voidMethod', 'arrayMethod', 'setProp', 'getProp', 'getFinalProp', 'getIntProp', 'setIntProp'] as Set

        def stringMethod = groovyClass.declaredMethods.find { it.name == 'stringMethod' }
        stringMethod.rawCommentText.contains('A method that returns String')
        stringMethod.returnType.signature == 'java.lang.String'
        stringMethod.parameters.collect { it.name } == ['stringParam']
        stringMethod.parameters[0].name == 'stringParam'
        stringMethod.parameters[0].type.signature == 'java.lang.String'

        def refTypeMethod = groovyClass.declaredMethods.find { it.name == 'refTypeMethod' }
        refTypeMethod.rawCommentText.contains('A method that returns a reference type.')
        refTypeMethod.returnType.signature == 'org.gradle.test.GroovyInterface'
        refTypeMethod.parameters.collect { it.name } == ['someThing', 'aFlag']
        refTypeMethod.parameters[0].name == 'someThing'
        refTypeMethod.parameters[0].type.signature == 'org.gradle.test.JavaInterface'
        refTypeMethod.parameters[1].name == 'aFlag'
        refTypeMethod.parameters[1].type.signature == 'boolean'

        def defMethod = groovyClass.declaredMethods.find { it.name == 'defMethod' }
        defMethod.rawCommentText.contains('A method that returns a default type.')
        defMethod.returnType.signature == 'java.lang.Object'
        defMethod.parameters.collect { it.name } == ['defParam']
        defMethod.parameters[0].name == 'defParam'
        defMethod.parameters[0].type.signature == 'java.lang.Object'

        def voidMethod = groovyClass.declaredMethods.find { it.name == 'voidMethod' }
        voidMethod.rawCommentText.contains('A method that returns void.')
        voidMethod.returnType.signature == 'void'
        voidMethod.parameters.collect { it.name } == []

        def arrayMethod = groovyClass.declaredMethods.find { it.name == 'arrayMethod' }
        arrayMethod.returnType.signature == 'java.lang.String[][]'
        arrayMethod.returnType.arrayDimensions == 2
        arrayMethod.parameters.collect { it.name } == ['strings']
        arrayMethod.parameters[0].name == 'strings'
        arrayMethod.parameters[0].type.signature == 'java.lang.String[]...'
        arrayMethod.parameters[0].type.arrayDimensions == 2

        def getProp = groovyClass.declaredMethods.find { it.name == 'getProp' }
        getProp.rawCommentText == ''
        getProp.returnType.signature == 'java.lang.String'
        getProp.parameters.collect { it.name } == []

        def setProp = groovyClass.declaredMethods.find { it.name == 'setProp' }
        setProp.rawCommentText == ''
        setProp.returnType.signature == 'void'
        setProp.parameters.collect { it.name } == ['prop']
        setProp.parameters[0].name == 'prop'
        setProp.parameters[0].type.signature == 'java.lang.String'

        def getFinalProp = groovyClass.declaredMethods.find { it.name == 'getFinalProp' }
        getFinalProp.rawCommentText == ''
        getFinalProp.returnType.signature == 'org.gradle.test.JavaInterface'
        getFinalProp.parameters.collect { it.name } == []

        groovyClass.declaredPropertyNames == ['prop', 'finalProp', 'intProp'] as Set
    }

    def extractsMethodMetaDataFromJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithMethods.java')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
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
        refTypeMethod.returnType.signature == 'org.gradle.test.GroovyInterface'
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

        javaClass.declaredPropertyNames == ['intProp'] as Set
    }

    def extractsConstantsFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithConstants.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithConstants')
        groovyClass.constants.keySet() == ['INT_CONST', 'STRING_CONST', 'OBJECT_CONST', 'BIG_DECIMAL_CONST'] as Set

        groovyClass.constants['INT_CONST'] == '9'
        groovyClass.constants['STRING_CONST'] == 'some-string'
        groovyClass.constants['BIG_DECIMAL_CONST'] == '1.02'
        groovyClass.constants['OBJECT_CONST'] == null
    }

    def extractsConstantsFromJavaClassSource() {
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

    def extractsConstantsFromJavaInterfaceSource() {
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

    def handlesFullyQualifiedNamesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithFullyQualifiedNames.groovy')
        task.source testFile('org/gradle/test/sub/SubJavaInterface.java')
        task.source testFile('org/gradle/test/sub/SubGroovyClass.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithFullyQualifiedNames')
        groovyClass.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        groovyClass.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'java.lang.Runnable']
        groovyClass.declaredPropertyNames == ['prop'] as Set

        def prop = groovyClass.findDeclaredProperty('prop')
        prop.type.signature == 'org.gradle.test.sub.SubJavaInterface'
    }

    def handlesFullyQualifiedNamesInJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithFullyQualifiedNames.java')
        task.source testFile('org/gradle/test/sub/SubJavaInterface.java')
        task.source testFile('org/gradle/test/sub/SubGroovyClass.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithFullyQualifiedNames')
        javaClass.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        javaClass.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'java.lang.Runnable']
        javaClass.declaredPropertyNames == ['prop'] as Set

        def prop = javaClass.findDeclaredProperty('prop')
        prop.type.signature == 'org.gradle.test.sub.SubJavaInterface'
    }

    def handlesImportedTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithImports.groovy')
        task.source testFile('org/gradle/test/sub/SubJavaInterface.java')
        task.source testFile('org/gradle/test/sub/SubGroovyClass.groovy')
        task.source testFile('org/gradle/test/sub2/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithImports')
        groovyClass.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        groovyClass.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'org.gradle.test.sub2.GroovyInterface']
        groovyClass.declaredPropertyNames == [] as Set
    }

    def handlesImportedTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithImports.java')
        task.source testFile('org/gradle/test/sub/SubJavaInterface.java')
        task.source testFile('org/gradle/test/sub/SubGroovyClass.groovy')
        task.source testFile('org/gradle/test/sub2/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithImports')
        javaClass.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        javaClass.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'org.gradle.test.sub2.GroovyInterface', 'java.io.Closeable']
        javaClass.declaredPropertyNames == [] as Set
    }

    def handlesEnumTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyEnum.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyEnum = repository.get('org.gradle.test.GroovyEnum')
        groovyEnum.groovy
        !groovyEnum.isInterface()
        groovyEnum.enum

        and:
        groovyEnum.declaredEnumConstants == ["A", "B"]
    }

    def handlesEnumTypesInJavaSource() {
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
        javaEnum.declaredEnumConstants == ["A", "B"]
    }

    def handlesAnnotationTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyAnnotation.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def annotation = repository.get('org.gradle.test.GroovyAnnotation')
        annotation.groovy
        !annotation.isInterface()
    }

    def handlesAnnotationTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def annotation = repository.get('org.gradle.test.JavaAnnotation')
        !annotation.groovy
        !annotation.isInterface()
    }

    def handlesNestedAndAnonymousTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithInnerTypes.groovy')
        task.source testFile('org/gradle/test/sub2/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithInnerTypes')
        groovyClass.interfaceNames == ['org.gradle.test.sub2.GroovyInterface']
        groovyClass.declaredPropertyNames == ['someProp', 'innerClassProp'] as Set

        def someProp = groovyClass.findDeclaredProperty('someProp')
        someProp.type.signature == 'org.gradle.test.sub2.GroovyInterface'

        def innerClassProp = groovyClass.findDeclaredProperty('innerClassProp')
        innerClassProp.type.signature == 'org.gradle.test.GroovyClassWithInnerTypes.InnerClass.AnotherInner'

        def innerEnum = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerEnum')
        innerEnum.rawCommentText.contains('This is an inner enum.')

        def innerClass = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerClass')
        innerClass.rawCommentText.contains('This is an inner class.')
        innerClass.declaredPropertyNames == ['enumProp'] as Set

        def enumProp = innerClass.findDeclaredProperty('enumProp')
        enumProp.type.signature == 'org.gradle.test.GroovyClassWithInnerTypes.InnerEnum'

        def anotherInner = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerClass.AnotherInner')
        anotherInner.rawCommentText.contains('This is an inner inner class.')
        anotherInner.declaredPropertyNames == ['outer'] as Set

        def outer = anotherInner.findDeclaredProperty('outer')
        outer.type.signature == 'org.gradle.test.GroovyClassWithInnerTypes.InnerClass'
    }

    def handlesNestedAndAnonymousTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithInnerTypes.java')
        task.source testFile('org/gradle/test/sub2/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithInnerTypes')
        javaClass.interfaceNames == ['org.gradle.test.sub2.GroovyInterface']
        javaClass.declaredPropertyNames == ['someProp', 'innerClassProp'] as Set

        def someProp = javaClass.findDeclaredProperty('someProp')
        someProp.type.signature == 'org.gradle.test.sub2.GroovyInterface'

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

    def handlesParameterizedTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithParameterizedTypes.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithParameterizedTypes')

        def setProp = groovyClass.findDeclaredProperty('setProp')
        setProp.type.signature == 'java.util.Set<org.gradle.test.GroovyInterface>'

        def mapProp = groovyClass.findDeclaredProperty('mapProp')
        mapProp.type.signature == 'java.util.Map<org.gradle.test.GroovyInterface, org.gradle.test.GroovyClassWithParameterizedTypes>'

        def wilcardProp = groovyClass.findDeclaredProperty('wildcardProp')
        wilcardProp.type.signature == 'java.util.List<?>'

        def upperBoundProp = groovyClass.findDeclaredProperty('upperBoundProp')
        upperBoundProp.type.signature == 'java.util.List<? extends org.gradle.test.GroovyInterface>'

        def lowerBoundProp = groovyClass.findDeclaredProperty('lowerBoundProp')
        lowerBoundProp.type.signature == 'java.util.List<? super org.gradle.test.GroovyInterface>'

        def nestedProp = groovyClass.findDeclaredProperty('nestedProp')
        nestedProp.type.signature == 'java.util.List<? super java.util.Set<? extends java.util.Map<?, org.gradle.test.GroovyInterface[]>>>[]'

        def paramMethod = groovyClass.declaredMethods.find { it.name == 'paramMethod' }
        paramMethod.returnType.signature == 'T'
        paramMethod.parameters[0].type.signature == 'T'
    }

    def handlesParameterizedTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithParameterizedTypes.java')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def javaClass = repository.get('org.gradle.test.JavaClassWithParameterizedTypes')

        def setProp = javaClass.findDeclaredProperty('setProp')
        setProp.type.signature == 'java.util.Set<org.gradle.test.GroovyInterface>'

        def mapProp = javaClass.findDeclaredProperty('mapProp')
        mapProp.type.signature == 'java.util.Map<org.gradle.test.GroovyInterface, org.gradle.test.JavaClassWithParameterizedTypes>'

        def wildcardProp = javaClass.findDeclaredProperty('wildcardProp')
        wildcardProp.type.signature == 'java.util.List<?>'

        def upperBoundProp = javaClass.findDeclaredProperty('upperBoundProp')
        upperBoundProp.type.signature == 'java.util.List<? extends org.gradle.test.GroovyInterface>'

        def lowerBoundProp = javaClass.findDeclaredProperty('lowerBoundProp')
        lowerBoundProp.type.signature == 'java.util.List<? super org.gradle.test.GroovyInterface>'

        def nestedProp = javaClass.findDeclaredProperty('nestedProp')
        nestedProp.type.signature == 'java.util.List<? super java.util.Set<? extends java.util.Map<?, org.gradle.test.GroovyInterface[]>>>[]'

        def paramMethod = javaClass.declaredMethods.find { it.name == 'paramMethod' }
        paramMethod.returnType.signature == 'T'
        paramMethod.parameters[0].type.signature == 'T'
    }

    def extractsClassAnnotationsFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithAnnotation.groovy')
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithAnnotation')
        groovyClass.annotationTypeNames == ['java.lang.Deprecated', 'org.gradle.test.JavaAnnotation']
    }

    def extractsClassAnnotationsFromJavaSource() {
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

    def extractsMethodAnnotationsFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithAnnotation.groovy')
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithAnnotation')
        def method = groovyClass.declaredMethods.find { it.name == 'annotatedMethod' }
        method.annotationTypeNames == ['java.lang.Deprecated', 'org.gradle.test.JavaAnnotation']
    }

    def extractsMethodAnnotationsFromJavaSource() {
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

    def extractsPropertyAnnotationsFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithAnnotation.groovy')
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def groovyClass = repository.get('org.gradle.test.GroovyClassWithAnnotation')
        def property = groovyClass.declaredProperties.find { it.name == 'annotatedProperty' }
        property.annotationTypeNames == ['java.lang.Deprecated', 'org.gradle.test.JavaAnnotation']
    }

    def extractsPropertyAnnotationsFromJavaSource() {
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

    def testFile(String fileName) {
        URL resource = getClass().classLoader.getResource(fileName)
        assert resource != null: "Could not find resource '$fileName'."
        assert resource.protocol == 'file'
        return new File(resource.toURI())
    }
}
