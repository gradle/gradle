/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath

import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.classpath.TypeCollectingClasspathFileTransformer.*

class TypeCollectingClasspathFileTransformerTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def classpathWalker = new ClasspathWalker(TestFiles.fileSystem())
    InstrumentingClasspathFileTransformer.Policy policy = InstrumentingClasspathFileTransformer.instrumentForLoadingWithClassLoader()

    @Subject
    TypeCollectingClasspathFileTransformer transformer = new TypeCollectingClasspathFileTransformer(classpathWalker, policy)

    def "should collect all hierarchy types"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def typeRegistry = new TypeRegistry()

        when:
        transformer.transform(dir, null, null, typeRegistry)

        then:
        def testName = TypeCollectingClasspathFileTransformerTest.name.replace('.', '/')
        typeRegistry.getDirectSuperTypes()[testName] ==~ ['groovy/lang/GroovyObject', 'spock/lang/Specification']
        typeRegistry.getSubTypes('groovy/lang/GroovyObject') ==~ [testName, 'groovy/lang/GroovyObject']
    }

    void classesDir(TestFile dir) {
        dir.deleteDir()
        dir.createDir()
        dir.file("a.class").bytes = classOne()
    }

    byte[] classOne() {
        return getClass().classLoader.getResource(TypeCollectingClasspathFileTransformerTest.name.replace('.', '/') + ".class").bytes
    }
}
