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
package org.gradle.build.docs.dsl

import org.gradle.api.Project
import org.gradle.build.docs.dsl.model.ClassMetaData
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
        def metaData = repository.get('org.gradle.test.GroovyClass')
        metaData.groovy
        !metaData.isInterface()
        metaData.rawCommentText.contains('This is a groovy class.')
        metaData.superClassName == 'org.gradle.test.A'
        metaData.interfaceNames == ['org.gradle.test.GroovyInterface', 'org.gradle.test.JavaInterface']

        metaData = repository.get('org.gradle.test.GroovyInterface')
        metaData.groovy
        metaData.isInterface()
        metaData.superClassName == null
        metaData.interfaceNames == ['org.gradle.test.Interface1', 'org.gradle.test.Interface2']
    }

    def extractsMetaDataFromJavaSource() {
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
        def metaData = repository.get('org.gradle.test.JavaClass')
        !metaData.groovy
        !metaData.isInterface()
        metaData.rawCommentText.contains('This is a java class.')
        metaData.superClassName == 'org.gradle.test.A'
        metaData.interfaceNames == ['org.gradle.test.GroovyInterface', 'org.gradle.test.JavaInterface']

        metaData = repository.get('org.gradle.test.JavaInterface')
        !metaData.groovy
        metaData.isInterface()
        metaData.superClassName == null
        metaData.interfaceNames == ['org.gradle.test.Interface1', 'org.gradle.test.Interface2']
    }

    def extractsPropertyMetaDataFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClass.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyClass')
        metaData.declaredPropertyNames == ['readOnly', 'writeOnly', 'someProp', 'groovyProp', 'readOnlyGroovyProp', 'arrayProp'] as Set

        def prop = metaData.findDeclaredProperty('readOnly')
        prop.type.signature == 'java.lang.Object'
        !prop.writeable
        prop.rawCommentText.contains('A read-only property.')

        prop = metaData.findDeclaredProperty('writeOnly')
        prop.type.signature == 'org.gradle.test.JavaInterface'
        prop.writeable
        prop.rawCommentText.contains('A write-only property.')

        prop = metaData.findDeclaredProperty('someProp')
        prop.type.signature == 'org.gradle.test.GroovyInterface'
        prop.writeable
        prop.rawCommentText.contains('A property.')

        prop = metaData.findDeclaredProperty('groovyProp')
        prop.type.signature == 'org.gradle.test.GroovyInterface'
        prop.writeable
        prop.rawCommentText.contains('A groovy property.')

        prop = metaData.findDeclaredProperty('readOnlyGroovyProp')
        prop.type.signature == 'java.lang.String'
        !prop.writeable
        prop.rawCommentText.contains('A read-only groovy property.')

        prop = metaData.findDeclaredProperty('arrayProp')
        prop.type.signature == 'java.lang.String[]'
        prop.writeable
        prop.rawCommentText.contains('An array property.')
    }

    def extractsPropertyMetaDataFromJavaSource() {
        task.source testFile('org/gradle/test/JavaClass.java')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaClass')
        metaData.declaredPropertyNames == ['readOnly', 'writeOnly', 'someProp', 'flag', 'arrayProp'] as Set

        def prop = metaData.findDeclaredProperty('readOnly')
        prop.type.signature == 'java.lang.String'
        !prop.writeable
        prop.rawCommentText.contains('A read-only property.')

        prop = metaData.findDeclaredProperty('writeOnly')
        prop.type.signature == 'org.gradle.test.JavaInterface'
        prop.writeable
        prop.rawCommentText.contains('A write-only property.')

        prop = metaData.findDeclaredProperty('someProp')
        prop.type.signature == 'org.gradle.test.JavaInterface'
        prop.writeable
        prop.rawCommentText.contains('A property.')

        prop = metaData.findDeclaredProperty('flag')
        prop.type.signature == 'boolean'
        !prop.writeable
        prop.rawCommentText.contains('A boolean property.')

        prop = metaData.findDeclaredProperty('arrayProp')

        prop.type.signature == 'org.gradle.test.JavaInterface[][][]'
        !prop.writeable
        prop.rawCommentText.contains('An array property.')
    }

    def extractsMethodMetaDataFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithMethods.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyClassWithMethods')
        metaData.declaredMethods.collect { it.name } as Set == ['stringMethod', 'refTypeMethod', 'defMethod', 'voidMethod', 'arrayMethod', 'setProp', 'getProp', 'getFinalProp', 'getIntProp', 'setIntProp'] as Set

        def method = metaData.declaredMethods.find { it.name == 'stringMethod' }
        method.rawCommentText.contains('A method that returns String')
        method.returnType.signature == 'java.lang.String'
        method.parameters.collect { it.name } == ['stringParam']

        def param = method.parameters[0]
        param.name == 'stringParam'
        param.type.signature == 'java.lang.String'

        method = metaData.declaredMethods.find { it.name == 'refTypeMethod' }
        method.rawCommentText.contains('A method that returns a reference type.')
        method.returnType.signature == 'org.gradle.test.GroovyInterface'
        method.parameters.collect { it.name } == ['someThing', 'aFlag']

        param = method.parameters[0]
        param.name == 'someThing'
        param.type.signature == 'org.gradle.test.JavaInterface'

        param = method.parameters[1]
        param.name == 'aFlag'
        param.type.signature == 'boolean'

        method = metaData.declaredMethods.find { it.name == 'defMethod' }
        method.rawCommentText.contains('A method that returns a default type.')
        method.returnType.signature == 'java.lang.Object'
        method.parameters.collect { it.name } == ['defParam']

        param = method.parameters[0]
        param.name == 'defParam'
        param.type.signature == 'java.lang.Object'

        method = metaData.declaredMethods.find { it.name == 'voidMethod' }
        method.rawCommentText.contains('A method that returns void.')
        method.returnType.signature == 'void'
        method.parameters.collect { it.name } == []

        method = metaData.declaredMethods.find { it.name == 'arrayMethod' }
        method.returnType.signature == 'java.lang.String[][]'
        method.returnType.arrayDimensions == 2
        method.parameters.collect { it.name } == ['strings']

        param = method.parameters[0]
        param.name == 'strings'
        param.type.signature == 'java.lang.String[]...'
        param.type.arrayDimensions == 2

        method = metaData.declaredMethods.find { it.name == 'getProp' }
        method.rawCommentText == ''
        method.returnType.signature == 'java.lang.String'
        method.parameters.collect { it.name } == []

        method = metaData.declaredMethods.find { it.name == 'setProp' }
        method.rawCommentText == ''
        method.returnType.signature == 'void'
        method.parameters.collect { it.name } == ['prop']

        param = method.parameters[0]
        param.name == 'prop'
        param.type.signature == 'java.lang.String'

        method = metaData.declaredMethods.find { it.name == 'getFinalProp' }
        method.rawCommentText == ''
        method.returnType.signature == 'org.gradle.test.JavaInterface'
        method.parameters.collect { it.name } == []

        metaData.declaredPropertyNames == ['prop', 'finalProp', 'intProp'] as Set
    }

    def extractsMethodMetaDataFromJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithMethods.java')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaClassWithMethods')
        metaData.declaredMethods.collect { it.name } as Set == ['stringMethod', 'refTypeMethod', 'voidMethod', 'arrayMethod', 'getIntProp', 'setIntProp'] as Set

        def method = metaData.declaredMethods.find { it.name == 'stringMethod' }
        method.rawCommentText.contains('A method that returns String')
        method.returnType.signature == 'java.lang.String'
        method.parameters.collect { it.name } == ['stringParam']

        def param = method.parameters[0]
        param.name == 'stringParam'
        param.type.signature == 'java.lang.String'

        method = metaData.declaredMethods.find { it.name == 'refTypeMethod' }
        method.rawCommentText.contains('A method that returns a reference type.')
        method.returnType.signature == 'org.gradle.test.GroovyInterface'
        method.parameters.collect { it.name } == ['refParam', 'aFlag']

        param = method.parameters[0]
        param.name == 'refParam'
        param.type.signature == 'org.gradle.test.JavaInterface'

        param = method.parameters[1]
        param.name == 'aFlag'
        param.type.signature == 'boolean'

        method = metaData.declaredMethods.find { it.name == 'voidMethod' }
        method.rawCommentText.contains('A method that returns void.')
        method.returnType.signature == 'void'
        method.parameters.collect { it.name } == []

        method = metaData.declaredMethods.find { it.name == 'arrayMethod' }
        method.returnType.signature == 'java.lang.String[][]'
        method.returnType.arrayDimensions == 2
        method.parameters.collect { it.name } == ['strings']

        param = method.parameters[0]
        param.name == 'strings'
        param.type.signature == 'java.lang.String[]...'
        param.type.arrayDimensions == 2

        metaData.declaredPropertyNames == ['intProp'] as Set
    }

    def extractsConstantsFromGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithConstants.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyClassWithConstants')
        metaData.constants.keySet() == ['INT_CONST', 'STRING_CONST', 'OBJECT_CONST', 'BIG_DECIMAL_CONST'] as Set

        metaData.constants['INT_CONST'] == '9'
        metaData.constants['STRING_CONST'] == 'some-string'
        metaData.constants['BIG_DECIMAL_CONST'] == '1.02'
        metaData.constants['OBJECT_CONST'] == null
    }

    def extractsConstantsFromJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithConstants.java')
        task.source testFile('org/gradle/test/JavaInterfaceWithConstants.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaClassWithConstants')
        metaData.constants.keySet() == ['INT_CONST', 'STRING_CONST', 'OBJECT_CONST', 'CHAR_CONST'] as Set

        metaData.constants['INT_CONST'] == '9'
        metaData.constants['STRING_CONST'] == 'some-string'
        metaData.constants['CHAR_CONST'] == 'a'
        metaData.constants['OBJECT_CONST'] == null

        metaData = repository.get('org.gradle.test.JavaInterfaceWithConstants')
        metaData.constants.keySet() == ['INT_CONST', 'STRING_CONST'] as Set

        metaData.constants['INT_CONST'] == '120'
        metaData.constants['STRING_CONST'] == 'some-string'
    }

    def handlesFullyQualifiedNamesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithFullyQualifiedNames.groovy')
        task.source testFile('org/gradle/test/sub/SubJavaInterface.java')
        task.source testFile('org/gradle/test/sub/SubGroovyClass.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyClassWithFullyQualifiedNames')
        metaData.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        metaData.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'java.lang.Runnable']
        metaData.declaredPropertyNames == ['prop'] as Set

        def prop = metaData.findDeclaredProperty('prop')
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
        def metaData = repository.get('org.gradle.test.JavaClassWithFullyQualifiedNames')
        metaData.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        metaData.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'java.lang.Runnable']
        metaData.declaredPropertyNames == ['prop'] as Set

        def prop = metaData.findDeclaredProperty('prop')
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
        def metaData = repository.get('org.gradle.test.GroovyClassWithImports')
        metaData.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        metaData.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'org.gradle.test.sub2.GroovyInterface']
        metaData.declaredPropertyNames == [] as Set
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
        def metaData = repository.get('org.gradle.test.JavaClassWithImports')
        metaData.superClassName == 'org.gradle.test.sub.SubGroovyClass'
        metaData.interfaceNames == ['org.gradle.test.sub.SubJavaInterface', 'org.gradle.test.sub2.GroovyInterface', 'java.io.Closeable']
        metaData.declaredPropertyNames == [] as Set
    }

    def handlesEnumTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyEnum.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyEnum')
        metaData.groovy
        !metaData.isInterface()
    }

    def handlesEnumTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaEnum.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaEnum')
        !metaData.groovy
        !metaData.isInterface()
    }

    def handlesAnnotationTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyAnnotation.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyAnnotation')
        metaData.groovy
        !metaData.isInterface()
    }

    def handlesAnnotationTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaAnnotation')
        !metaData.groovy
        !metaData.isInterface()
    }

    def handlesNestedAndAnonymousTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithInnerTypes.groovy')
        task.source testFile('org/gradle/test/sub2/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyClassWithInnerTypes')
        metaData.interfaceNames == ['org.gradle.test.sub2.GroovyInterface']
        metaData.declaredPropertyNames == ['someProp', 'innerClassProp'] as Set

        def propMetaData = metaData.findDeclaredProperty('someProp')
        propMetaData.type.signature == 'org.gradle.test.sub2.GroovyInterface'

        propMetaData = metaData.findDeclaredProperty('innerClassProp')
        propMetaData.type.signature == 'org.gradle.test.GroovyClassWithInnerTypes.InnerClass.AnotherInner'

        metaData = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerEnum')
        metaData.rawCommentText.contains('This is an inner enum.')

        metaData = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerClass')
        metaData.rawCommentText.contains('This is an inner class.')
        metaData.declaredPropertyNames == ['enumProp'] as Set

        propMetaData = metaData.findDeclaredProperty('enumProp')
        propMetaData.type.signature == 'org.gradle.test.GroovyClassWithInnerTypes.InnerEnum'

        metaData = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerClass.AnotherInner')
        metaData.rawCommentText.contains('This is an inner inner class.')
        metaData.declaredPropertyNames == ['outer'] as Set

        propMetaData = metaData.findDeclaredProperty('outer')
        propMetaData.type.signature == 'org.gradle.test.GroovyClassWithInnerTypes.InnerClass'
    }

    def handlesNestedAndAnonymousTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithInnerTypes.java')
        task.source testFile('org/gradle/test/sub2/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaClassWithInnerTypes')
        metaData.interfaceNames == ['org.gradle.test.sub2.GroovyInterface']
        metaData.declaredPropertyNames == ['someProp', 'innerClassProp'] as Set

        def propMetaData = metaData.findDeclaredProperty('someProp')
        propMetaData.type.signature == 'org.gradle.test.sub2.GroovyInterface'

        propMetaData = metaData.findDeclaredProperty('innerClassProp')
        propMetaData.type.signature == 'org.gradle.test.JavaClassWithInnerTypes.InnerClass.AnotherInner'

        metaData = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerEnum')
        metaData.rawCommentText.contains('This is an inner enum.')

        metaData = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerClass')
        metaData.rawCommentText.contains('This is an inner class.')
        metaData.declaredPropertyNames == ['enumProp'] as Set

        propMetaData = metaData.findDeclaredProperty('enumProp')
        propMetaData.type.signature == 'org.gradle.test.JavaClassWithInnerTypes.InnerEnum'

        metaData = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerClass.AnotherInner')
        metaData.rawCommentText.contains('This is an inner inner class.')
        metaData.declaredPropertyNames == ['outer'] as Set

        propMetaData = metaData.findDeclaredProperty('outer')
        propMetaData.type.signature == 'org.gradle.test.JavaClassWithInnerTypes.InnerClass'
    }

    def handlesParameterizedTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyClassWithParameterizedTypes.groovy')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyClassWithParameterizedTypes')

        def property = metaData.findDeclaredProperty('setProp')
        property.type.signature == 'java.util.Set<org.gradle.test.GroovyInterface>'

        property = metaData.findDeclaredProperty('mapProp')
        property.type.signature == 'java.util.Map<org.gradle.test.GroovyInterface, org.gradle.test.GroovyClassWithParameterizedTypes>'

        property = metaData.findDeclaredProperty('wildcardProp')
        property.type.signature == 'java.util.List<?>'

        property = metaData.findDeclaredProperty('upperBoundProp')
        property.type.signature == 'java.util.List<? extends org.gradle.test.GroovyInterface>'

        property = metaData.findDeclaredProperty('lowerBoundProp')
        property.type.signature == 'java.util.List<? super org.gradle.test.GroovyInterface>'

        property = metaData.findDeclaredProperty('nestedProp')
        property.type.signature == 'java.util.List<? super java.util.Set<? extends java.util.Map<?, org.gradle.test.GroovyInterface[]>>>[]'

        def method = metaData.declaredMethods.find { it.name == 'paramMethod' }
        method.returnType.signature == 'T'
        method.parameters[0].type.signature == 'T'
    }

    def handlesParameterizedTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaClassWithParameterizedTypes.java')
        task.source testFile('org/gradle/test/GroovyInterface.groovy')
        task.source testFile('org/gradle/test/JavaInterface.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaClassWithParameterizedTypes')

        def property = metaData.findDeclaredProperty('setProp')
        property.type.signature == 'java.util.Set<org.gradle.test.GroovyInterface>'

        property = metaData.findDeclaredProperty('mapProp')
        property.type.signature == 'java.util.Map<org.gradle.test.GroovyInterface, org.gradle.test.JavaClassWithParameterizedTypes>'

        property = metaData.findDeclaredProperty('wildcardProp')
        property.type.signature == 'java.util.List<?>'

        property = metaData.findDeclaredProperty('upperBoundProp')
        property.type.signature == 'java.util.List<? extends org.gradle.test.GroovyInterface>'

        property = metaData.findDeclaredProperty('lowerBoundProp')
        property.type.signature == 'java.util.List<? super org.gradle.test.GroovyInterface>'

        property = metaData.findDeclaredProperty('nestedProp')
        property.type.signature == 'java.util.List<? super java.util.Set<? extends java.util.Map<?, org.gradle.test.GroovyInterface[]>>>[]'

        def method = metaData.declaredMethods.find { it.name == 'paramMethod' }
        method.returnType.signature == 'T'

        def param = method.parameters[0]
        param.type.signature == 'T'
    }

    def testFile(String fileName) {
        URL resource = getClass().classLoader.getResource(fileName)
        assert resource != null
        assert resource.protocol == 'file'
        return new File(resource.path)
    }
}
