package org.gradle.api.internal.tasks

import org.junit.Test
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import static org.gradle.util.WrapUtil.*
import org.gradle.api.internal.file.FileResolver

class DefaultGroovySourceSetTest {
    private final DefaultGroovySourceSet sourceSet = new DefaultGroovySourceSet("<set-display-name>", [resolve: {it as File}] as FileResolver)
    
    @Test
    public void defaultValues() {
        assertThat(sourceSet.groovy, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.groovy, isEmpty())
        assertThat(sourceSet.groovy.displayName, equalTo('<set-display-name> groovy source'))

        assertThat(sourceSet.allGroovy, instanceOf(UnionFileTree))
        assertThat(sourceSet.allGroovy, isEmpty())
        assertThat(sourceSet.allGroovy.displayName, equalTo('<set-display-name> groovy source'))
        assertThat(sourceSet.allGroovy.sourceCollections, equalTo(toLinkedSet(sourceSet.groovy)))
    }
}
