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
import org.gradle.api.internal.file.UnionFileTree
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class DefaultGroovySourceSetTest {
    private final DefaultGroovySourceSet sourceSet = new DefaultGroovySourceSet("<set-display-name>", [resolve: {it as File}] as FileResolver)
    
    @Test
    public void defaultValues() {
        assertThat(sourceSet.groovy, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.groovy, isEmpty())
        assertThat(sourceSet.groovy.displayName, equalTo('<set-display-name> Groovy source'))

        assertThat(sourceSet.groovySourcePatterns.includes, equalTo(['**/*.groovy'] as Set))
        assertThat(sourceSet.groovySourcePatterns.excludes, isEmpty())

        assertThat(sourceSet.allGroovy, instanceOf(UnionFileTree))
        assertThat(sourceSet.allGroovy, isEmpty())
        assertThat(sourceSet.allGroovy.displayName, equalTo('<set-display-name> Groovy source'))
        assertThat(sourceSet.allGroovy.sourceTrees, not(isEmpty()))
    }

    @Test
    public void canConfigureGroovySource() {
        sourceSet.groovy { srcDir 'src/groovy' }
        assertThat(sourceSet.groovy.srcDirs, equalTo([new File('src/groovy')] as Set))
    }
}
