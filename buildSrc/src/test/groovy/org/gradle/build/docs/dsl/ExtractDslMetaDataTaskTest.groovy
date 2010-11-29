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
import spock.lang.Ignore

class ExtractDslMetaDataTaskTest extends Specification {
    final Project project = new ProjectBuilder().build()
    final ExtractDslMetaDataTask task = project.tasks.add('dsl', ExtractDslMetaDataTask.class)
    final SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<ClassMetaData>()

    def setup() {
        task.destFile = project.file('meta-data.bin')
    }

    def extractsMetaDataFromGroovySource() {
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
        metaData.classProperties.keySet() == ['readOnly', 'writeOnly', 'someProp', 'groovyProp', 'readOnlyGroovyProp'] as Set

        def prop = metaData.classProperties['readOnly']
        prop.type == 'java.lang.Object'
        !prop.writeable
        prop.rawCommentText.contains('A read-only property.')

        prop = metaData.classProperties['writeOnly']
        prop.type == 'org.gradle.test.JavaInterface'
        prop.writeable
        prop.rawCommentText.contains('A write-only property.')

        prop = metaData.classProperties['someProp']
        prop.type == 'org.gradle.test.GroovyInterface'
        prop.writeable
        prop.rawCommentText.contains('A property.')

        prop = metaData.classProperties['groovyProp']
        prop.type == 'org.gradle.test.GroovyInterface'
        prop.writeable
        prop.rawCommentText.contains('A groovy property.')

        prop = metaData.classProperties['readOnlyGroovyProp']
        prop.type == 'java.lang.String'
        !prop.writeable
        prop.rawCommentText.contains('A read-only groovy property.')

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
        metaData.classProperties.keySet() == ['readOnly', 'writeOnly', 'someProp', 'flag'] as Set

        def prop = metaData.classProperties['readOnly']
        prop.type == 'java.lang.String'
        !prop.writeable
        prop.rawCommentText.contains('A read-only property.')

        prop = metaData.classProperties['writeOnly']
        prop.type == 'org.gradle.test.JavaInterface'
        prop.writeable
        prop.rawCommentText.contains('A write-only property.')

        prop = metaData.classProperties['someProp']
        prop.type == 'org.gradle.test.JavaInterface'
        prop.writeable
        prop.rawCommentText.contains('A property.')

        prop = metaData.classProperties['flag']
        prop.type == 'boolean'
        !prop.writeable
        prop.rawCommentText.contains('A boolean property.')

        metaData = repository.get('org.gradle.test.JavaInterface')
        !metaData.groovy
        metaData.isInterface()
        metaData.superClassName == null
        metaData.interfaceNames == ['org.gradle.test.Interface1', 'org.gradle.test.Interface2']
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
        metaData.classProperties.keySet() == ['prop'] as Set

        def prop = metaData.classProperties['prop']
        prop.type == 'org.gradle.test.sub.SubJavaInterface'
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
        metaData.classProperties.keySet() == ['prop'] as Set

        def prop = metaData.classProperties['prop']
        prop.type == 'org.gradle.test.sub.SubJavaInterface'
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
        metaData.classProperties.keySet() == [] as Set
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
        metaData.classProperties.keySet() == [] as Set
    }

    def handlesEnumTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyEnum.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyEnum')
        metaData != null
    }

    def handlesEnumTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaEnum.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaEnum')
        metaData != null
    }

    def handlesAnnotationTypesInGroovySource() {
        task.source testFile('org/gradle/test/GroovyAnnotation.groovy')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.GroovyAnnotation')
        metaData != null
    }

    def handlesAnnotationTypesInJavaSource() {
        task.source testFile('org/gradle/test/JavaAnnotation.java')

        when:
        task.extract()
        repository.load(task.destFile)

        then:
        def metaData = repository.get('org.gradle.test.JavaAnnotation')
        metaData != null
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
        metaData.classProperties.keySet() == ['someProp', 'innerClassProp'] as Set

        def propMetaData = metaData.classProperties['someProp']
        propMetaData.type == 'org.gradle.test.sub2.GroovyInterface'

        propMetaData = metaData.classProperties['innerClassProp']
        propMetaData.type == 'org.gradle.test.GroovyClassWithInnerTypes.InnerClass.AnotherInner'

        metaData = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerEnum')
        metaData.rawCommentText.contains('This is an inner enum.')

        metaData = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerClass')
        metaData.rawCommentText.contains('This is an inner class.')
        metaData.classProperties.keySet() == ['enumProp'] as Set

        propMetaData = metaData.classProperties['enumProp']
        propMetaData.type == 'org.gradle.test.GroovyClassWithInnerTypes.InnerEnum'

        metaData = repository.get('org.gradle.test.GroovyClassWithInnerTypes.InnerClass.AnotherInner')
        metaData.rawCommentText.contains('This is an inner inner class.')
        metaData.classProperties.keySet() == ['outer'] as Set

        propMetaData = metaData.classProperties['outer']
        propMetaData.type == 'org.gradle.test.GroovyClassWithInnerTypes.InnerClass'
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
        metaData.classProperties.keySet() == ['someProp', 'innerClassProp'] as Set

        def propMetaData = metaData.classProperties['someProp']
        propMetaData.type == 'org.gradle.test.sub2.GroovyInterface'

        propMetaData = metaData.classProperties['innerClassProp']
        propMetaData.type == 'org.gradle.test.JavaClassWithInnerTypes.InnerClass.AnotherInner'

        metaData = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerEnum')
        metaData.rawCommentText.contains('This is an inner enum.')

        metaData = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerClass')
        metaData.rawCommentText.contains('This is an inner class.')
        metaData.classProperties.keySet() == ['enumProp'] as Set

        propMetaData = metaData.classProperties['enumProp']
        propMetaData.type == 'org.gradle.test.JavaClassWithInnerTypes.InnerEnum'

        metaData = repository.get('org.gradle.test.JavaClassWithInnerTypes.InnerClass.AnotherInner')
        metaData.rawCommentText.contains('This is an inner inner class.')
        metaData.classProperties.keySet() == ['outer'] as Set

        propMetaData = metaData.classProperties['outer']
        propMetaData.type == 'org.gradle.test.JavaClassWithInnerTypes.InnerClass'
    }

    @Ignore
    def handlesImplicitImportsInGroovySource() {
        expect: false
    }

    def testFile(String fileName) {
        URL resource = getClass().classLoader.getResource(fileName)
        assert resource != null
        assert resource.protocol == 'file'
        return new File(resource.path)
    }
}
