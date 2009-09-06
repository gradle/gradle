package org.gradle.api.internal.tasks

import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.PathResolvingFileCollection
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.tasks.SourceSet
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class DefaultSourceSetTest {
    private final FileResolver resolver = [resolve: {it as File}] as FileResolver

    @Test public void defaultValues() {
        SourceSet sourceSet = new DefaultSourceSet('set-name', '<set-display-name>', resolver)

        assertThat(sourceSet.classesDir, nullValue())

        assertThat(sourceSet.compileClasspath, instanceOf(PathResolvingFileCollection))
        assertThat(sourceSet.compileClasspath, isEmpty())

        assertThat(sourceSet.runtimeClasspath, instanceOf(PathResolvingFileCollection))
        assertThat(sourceSet.runtimeClasspath, isEmpty())

        assertThat(sourceSet.resources, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.resources, isEmpty())
        assertThat(sourceSet.resources.displayName, equalTo('<set-display-name> resources'))

        assertThat(sourceSet.java, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.java, isEmpty())
        assertThat(sourceSet.java.displayName, equalTo('<set-display-name> Java source'))

        assertThat(sourceSet.javaSourcePatterns.includes, equalTo(['**/*.java'] as Set))
        assertThat(sourceSet.javaSourcePatterns.excludes, isEmpty())

        assertThat(sourceSet.allJava, instanceOf(UnionFileTree))
        assertThat(sourceSet.allJava, isEmpty())
        assertThat(sourceSet.allJava.displayName, equalTo('<set-display-name> Java source'))
        assertThat(sourceSet.allJava.sourceCollections, not(isEmpty()))

        assertThat(sourceSet.compileTaskName, equalTo('compileSetName'))
        assertThat(sourceSet.processResourcesTaskName, equalTo('processSetNameResources'))
    }
    
    @Test public void mainSourceSetUsesSpecialCaseTaskNames() {
        SourceSet sourceSet = new DefaultSourceSet('main', '<set-display-name>', resolver)

        assertThat(sourceSet.compileTaskName, equalTo('compile'))
        assertThat(sourceSet.processResourcesTaskName, equalTo('processResources'))
    }
}
