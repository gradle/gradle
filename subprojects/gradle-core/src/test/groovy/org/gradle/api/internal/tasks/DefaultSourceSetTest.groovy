package org.gradle.api.internal.tasks

import org.junit.Test
import org.gradle.api.tasks.SourceSet
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.DefaultFileCollection
import org.gradle.api.internal.file.FileResolver

class DefaultSourceSetTest {
    private final SourceSet sourceSet = new DefaultSourceSet('name', '<set-display-name>', [:] as FileResolver)

    @Test public void defaultValues() {
        assertThat(sourceSet.classesDir, nullValue())

        assertThat(sourceSet.compileClasspath, instanceOf(DefaultFileCollection))
        assertThat(sourceSet.compileClasspath, isEmpty())

        assertThat(sourceSet.runtimeClasspath, instanceOf(DefaultFileCollection))
        assertThat(sourceSet.runtimeClasspath, isEmpty())

        assertThat(sourceSet.resources, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.resources, isEmpty())
        assertThat(sourceSet.resources.displayName, equalTo('<set-display-name> resources'))

        assertThat(sourceSet.javaSource, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.javaSource, isEmpty())
        assertThat(sourceSet.javaSource.displayName, equalTo('<set-display-name> java source'))
    }
}
