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

class DefaultScalaSourceSetTest {

    @Before
    void before() {
        NativeServicesTestFixture.initialize()
    }

    private final DefaultScalaSourceSet sourceSet = new DefaultScalaSourceSet("<set-display-name>", [resolve: { it as File }] as FileResolver)

    @Test
    public void defaultValues() {
        assertThat(sourceSet.scala, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.scala, isEmpty())
        assertThat(sourceSet.scala.displayName, equalTo('<set-display-name> Scala source'))
        assertThat(sourceSet.scala.filter.includes, equalTo(['**/*.scala', '**/*.java'] as Set))
        assertThat(sourceSet.scala.filter.excludes, isEmpty())

        assertThat(sourceSet.allScala, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.allScala, isEmpty())
        assertThat(sourceSet.allScala.displayName, equalTo('<set-display-name> Scala source'))
        assertThat(sourceSet.allScala.source, hasItem(sourceSet.scala))
        assertThat(sourceSet.allScala.filter.includes, equalTo(['**/*.scala'] as Set))
        assertThat(sourceSet.allScala.filter.excludes, isEmpty())
    }

    @Test
    public void canConfigureScalaSource() {
        sourceSet.scala { srcDir 'src/scala' }
        assertThat(sourceSet.scala.srcDirs, equalTo([new File('src/scala').canonicalFile] as Set))
    }
}
