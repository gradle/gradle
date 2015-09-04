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

import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Before
import org.junit.Test

import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class DefaultGroovySourceSetTest {
    private final DefaultGroovySourceSet sourceSet = new DefaultGroovySourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

    @Before
    void before() {
        NativeServicesTestFixture.initialize()
    }

    @Test
    public void defaultValues() {
        assertThat(sourceSet.groovy, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.groovy, isEmpty())
        assertThat(sourceSet.groovy.displayName, equalTo('<set-display-name> Groovy source'))
        assertThat(sourceSet.groovy.filter.includes, equalTo(['**/*.groovy', '**/*.java'] as Set))
        assertThat(sourceSet.groovy.filter.excludes, isEmpty())

        assertThat(sourceSet.allGroovy, isEmpty())
        assertThat(sourceSet.allGroovy.displayName, equalTo('<set-display-name> Groovy source'))
        assertThat(sourceSet.allGroovy.source, hasItem(sourceSet.groovy))
        assertThat(sourceSet.allGroovy.filter.includes, equalTo(['**/*.groovy'] as Set))
        assertThat(sourceSet.allGroovy.filter.excludes, isEmpty())
    }

    @Test
    public void canConfigureGroovySource() {
        sourceSet.groovy { srcDir 'src/groovy' }
        assertThat(sourceSet.groovy.srcDirs, equalTo([new File('src/groovy').canonicalFile] as Set))
    }
}
