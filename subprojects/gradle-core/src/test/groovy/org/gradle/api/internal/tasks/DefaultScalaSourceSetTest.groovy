package org.gradle.api.internal.tasks

import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.UnionFileTree
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class DefaultScalaSourceSetTest {
    private final DefaultScalaSourceSet sourceSet = new DefaultScalaSourceSet("<set-display-name>", [resolve: {it as File}] as FileResolver)

    @Test
    public void defaultValues() {
        assertThat(sourceSet.scala, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.scala, isEmpty())
        assertThat(sourceSet.scala.displayName, equalTo('<set-display-name> Scala source'))

        assertThat(sourceSet.scalaSourcePatterns.includes, equalTo(['**/*.scala'] as Set))
        assertThat(sourceSet.scalaSourcePatterns.excludes, isEmpty())

        assertThat(sourceSet.allScala, instanceOf(UnionFileTree))
        assertThat(sourceSet.allScala, isEmpty())
        assertThat(sourceSet.allScala.displayName, equalTo('<set-display-name> Scala source'))
        assertThat(sourceSet.allScala.sourceTrees, not(isEmpty()))
    }

    @Test
    public void canConfigureScalaSource() {
        sourceSet.scala { srcDir 'src/scala' }
        assertThat(sourceSet.scala.srcDirs, equalTo([new File('src/scala')] as Set))
    }
}