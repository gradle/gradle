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
