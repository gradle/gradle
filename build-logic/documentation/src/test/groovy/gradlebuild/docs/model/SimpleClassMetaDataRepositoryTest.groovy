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
package gradlebuild.docs.model

import java.nio.file.Files
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import spock.lang.Specification
import spock.lang.TempDir

class SimpleClassMetaDataRepositoryTest extends Specification {
    @TempDir File tmpDir
    final SimpleClassMetaDataRepository<TestDomainObject> repository = new SimpleClassMetaDataRepository<TestDomainObject>()

    def canAddMetaData() {
        TestDomainObject value = new TestDomainObject('a')

        when:
        repository.put('class', value)

        then:
        repository.find('class') == value
        repository.get('class') == value
    }

    def findReturnsNullForUnknownClass() {
        expect:
        repository.find('unknown') == null
    }

    def getFailsForUnknownClass() {
        given:
        repository.put('unkown', new TestDomainObject('unknown'))

        when:
        repository.get('unknown')

        then:
        UnknownDomainObjectException e = thrown()
        e.message == 'No meta-data is available for class \'unknown\'. Did you mean? [unkown]'
    }

    def getFailsForWrongPackage() {
        given:
        repository.put('org.gradle.jvm.test.JUnitTestSuiteSpec', new TestDomainObject('org.gradle.jvm.test.JUnitTestSuiteSpec'))

        when:
        repository.get('org.gradle.jvm.JUnitTestSuiteSpec')

        then:
        UnknownDomainObjectException e = thrown()
        e.message == 'No meta-data is available for class \'org.gradle.jvm.JUnitTestSuiteSpec\'. Did you mean? [org.gradle.jvm.test.JUnitTestSuiteSpec]'
    }

    def canIterateOverClassesUsingClosure() {
        TestDomainObject value1 = new TestDomainObject('a')
        TestDomainObject value2 = new TestDomainObject('a')
        repository.put('class1', value1)
        repository.put('class2', value2)
        Closure cl = Mock()

        when:
        repository.each(cl)

        then:
        1 * cl.call(['class1', value1] as Object[])
        1 * cl.call(['class2', value2] as Object[])
        0 * cl._
    }

    def canIterateOverClassesUsingAction() {
        TestDomainObject value1 = new TestDomainObject('a')
        TestDomainObject value2 = new TestDomainObject('a')
        repository.put('class1', value1)
        repository.put('class2', value2)
        Action action = Mock()

        when:
        repository.each(action)

        then:
        1 * action.execute(value1)
        1 * action.execute(value2)
        0 * action._
    }

    def canPersistMetaData() {
        TestDomainObject value = new TestDomainObject('a')
        File file = Files.createTempFile(tmpDir.toPath(), null, null).toFile()
        repository.put('class', value)

        when:
        repository.store(file)
        def newRepo = new SimpleClassMetaDataRepository<String>()
        newRepo.load(file)

        then:
        newRepo.find('class') == value
    }
}

class TestDomainObject implements Attachable<TestDomainObject>, Serializable {
    def value

    TestDomainObject(String value) {
        this.value = value
    }

    @Override
    boolean equals(Object o) {
        return o.value == value
    }

    @Override
    int hashCode() {
        return value.hashCode()
    }

    void attach(ClassMetaDataRepository<TestDomainObject> repository) {
    }
}
