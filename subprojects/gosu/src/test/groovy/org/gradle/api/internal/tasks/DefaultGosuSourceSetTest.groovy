/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class DefaultGosuSourceSetTest {
    public @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Before
    void before() {
        NativeServicesTestFixture.initialize()
    }

    private final DefaultGosuSourceSet sourceSet = new DefaultGosuSourceSet("<set-display-name>", TestFiles.sourceDirectorySetFactory(tmpDir.testDirectory))

    @Test
    public void defaultValues() {
        assertThat(sourceSet.gosu, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.gosu, isEmpty())
        assertThat(sourceSet.gosu.displayName, equalTo('<set-display-name> Gosu source'))
        assertThat(sourceSet.gosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'] as Set))
        assertThat(sourceSet.gosu.filter.excludes, isEmpty())

        assertThat(sourceSet.allGosu, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.allGosu, isEmpty())
        assertThat(sourceSet.allGosu.displayName, equalTo('<set-display-name> Gosu source'))
        assertThat(sourceSet.allGosu.source, hasItem(sourceSet.gosu))
        assertThat(sourceSet.allGosu.filter.includes, equalTo(['**/*.gs', '**/*.gsx', '**/*.gst', '**/*.gsp'] as Set))
        assertThat(sourceSet.allGosu.filter.excludes, isEmpty())
    }

    @Test
    public void canConfigureGosuSource() {
        sourceSet.gosu { srcDir 'src/gosu' }
        assertThat(sourceSet.gosu.srcDirs, equalTo([tmpDir.file('src/gosu')] as Set))
    }
}
