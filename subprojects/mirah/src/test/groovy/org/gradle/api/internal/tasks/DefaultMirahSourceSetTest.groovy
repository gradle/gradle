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
import org.junit.Test
import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class DefaultMirahSourceSetTest {
    static {
        NativeServicesTestFixture.initialize()
    }

    private final DefaultMirahSourceSet sourceSet = new DefaultMirahSourceSet("<set-display-name>", [resolve: {it as File}] as FileResolver)

    @Test
    public void defaultValues() {
        assertThat(sourceSet.mirah, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.mirah, isEmpty())
        assertThat(sourceSet.mirah.displayName, equalTo('<set-display-name> Mirah source'))
        assertThat(sourceSet.mirah.filter.includes, equalTo(['**/*.mirah'] as Set))
        assertThat(sourceSet.mirah.filter.excludes, isEmpty())

        assertThat(sourceSet.allMirah, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.allMirah, isEmpty())
        assertThat(sourceSet.allMirah.displayName, equalTo('<set-display-name> Mirah source'))
        assertThat(sourceSet.allMirah.source, hasItem(sourceSet.mirah))
        assertThat(sourceSet.allMirah.filter.includes, equalTo(['**/*.mirah'] as Set))
        assertThat(sourceSet.allMirah.filter.excludes, isEmpty())
    }

    @Test
    public void canConfigureMirahSource() {
        sourceSet.mirah { srcDir 'src/mirah' }
        assertThat(sourceSet.mirah.srcDirs, equalTo([new File('src/mirah').canonicalFile] as Set))
    }
}