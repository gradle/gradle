/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks

import org.gradle.api.Action
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.GroovySourceSet
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.CollectionUtils
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.reflect.TypeOf.typeOf

class DefaultGroovySourceSetTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def sourceSet = TestUtil.newInstance(DefaultGroovySourceSet, "<name>", "<display-name>", TestUtil.objectFactory(tmpDir.testDirectory))

    void defaultValues() {
        expect:
        sourceSet.groovy instanceof GroovySourceDirectorySet
        sourceSet.groovy.isEmpty()
        sourceSet.groovy.name == '<name>'
        sourceSet.groovy.displayName == '<display-name> Groovy source'
        def includes = sourceSet.groovy.filter.includes
        includes.size() == 2 && includes.containsAll(['**/*.groovy', '**/*.java'])
        sourceSet.groovy.filter.excludes.isEmpty()

        sourceSet.allGroovy.isEmpty()
        sourceSet.allGroovy.displayName =='<display-name> Groovy source'
        sourceSet.allGroovy.srcDirs.containsAll(sourceSet.groovy.files)
        sourceSet.allGroovy.filter.includes.containsAll(['**/*.groovy'])
        sourceSet.allGroovy.filter.excludes.isEmpty()
    }

    void canConfigureGroovySource() {
        sourceSet.groovy {
            srcDir 'src/groovy'
        }
        expect:
        CollectionUtils.single(sourceSet.groovy.srcDirs) == tmpDir.file("src/groovy")
    }

    void canConfigureGroovySourceUsingAnAction() {
        sourceSet.groovy({ set ->
            set.srcDir 'src/groovy'
        } as Action<SourceDirectorySet>)

        expect:
        CollectionUtils.single(sourceSet.groovy.srcDirs) == tmpDir.file("src/groovy")
    }

    void exposesConventionPublicType() {
        sourceSet.publicType == typeOf(GroovySourceSet)
    }
}
