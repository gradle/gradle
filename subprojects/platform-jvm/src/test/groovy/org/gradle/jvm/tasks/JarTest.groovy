/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.jvm.tasks

import org.gradle.api.file.RelativePath
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.AbstractArchiveTaskTest

class JarTest extends AbstractArchiveTaskTest {
    Jar jar

    def setup()  {
        jar = createTask(Jar)
        configure(jar)
    }

    @Override
    AbstractArchiveTask getArchiveTask() {
        jar
    }

    def "test Jar"() {
        expect:
        jar.extension == Jar.DEFAULT_EXTENSION
        jar.manifest != null
        jar.metaInf != null
    }

    def "correct jar manifest"() {
        when:
        jar.manifest = new DefaultManifest(null);
        jar.manifest {
            attributes(key: 'value')
        }

        then:
        jar.manifest.attributes.key == 'value'
    }

    def "correct jar manifest from null manifest"() {
        when:
        jar.manifest = null
        jar.manifest {
            attributes(key: 'value')
        }

        then:
        jar.manifest.attributes.key == 'value'
    }

    def "comparator sorts manifest first"() {
        expect:
        List<RelativePath> sorted = [new RelativePath(false, 'META-INF')] +
            ['ABB.MF', 'AAA.META', 'MANIFEST.MF'].collect { new RelativePath(true, 'META-INF', it)} +
            ['Some.class', 'AaaClass.class'].collect { new RelativePath(true, it)}
        sorted.sort(true, Jar.JAR_CONTENTS_COMPARATOR)
        sorted*.pathString ==
            [
                    'META-INF',
                    'META-INF/MANIFEST.MF',
                    'META-INF/AAA.META',
                    'META-INF/ABB.MF',
                    'AaaClass.class',
                    'Some.class',
            ]
    }
}
