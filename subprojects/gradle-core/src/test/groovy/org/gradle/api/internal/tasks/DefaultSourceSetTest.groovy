package org.gradle.api.internal.tasks

import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.tasks.SourceSet
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.Task

class DefaultSourceSetTest {
    private final FileResolver fileResolver = [resolve: {it as File}] as FileResolver
    private final TaskResolver taskResolver = [resolveTask: {name -> [getName: {name}] as Task}] as TaskResolver

    @Test
    public void hasUsefulDisplayName() {
        SourceSet sourceSet = new DefaultSourceSet('int-test', fileResolver, taskResolver)
        assertThat(sourceSet.toString(), equalTo('source set int test'));
    }

    @Test public void defaultValues() {
        SourceSet sourceSet = new DefaultSourceSet('set-name', fileResolver, taskResolver)

        assertThat(sourceSet.classesDir, nullValue())
        assertThat(sourceSet.classes.files, isEmpty())
        assertThat(sourceSet.classes.displayName, equalTo('set name classes'))
        assertThat(sourceSet.classes.toString(), equalTo('set name classes'))
        assertThat(sourceSet.classes.buildDependencies.getDependencies(null), isEmpty())

        assertThat(sourceSet.compileClasspath, nullValue())

        assertThat(sourceSet.runtimeClasspath, nullValue())

        assertThat(sourceSet.resources, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.resources, isEmpty())
        assertThat(sourceSet.resources.displayName, equalTo('set name resources'))
        assertThat(sourceSet.resources.toString(), equalTo('set name resources'))

        assertThat(sourceSet.java, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.java, isEmpty())
        assertThat(sourceSet.java.displayName, equalTo('set name Java source'))
        assertThat(sourceSet.java.toString(), equalTo('set name Java source'))

        assertThat(sourceSet.javaSourcePatterns.includes, equalTo(['**/*.java'] as Set))
        assertThat(sourceSet.javaSourcePatterns.excludes, isEmpty())

        assertThat(sourceSet.allJava, instanceOf(UnionFileTree))
        assertThat(sourceSet.allJava, isEmpty())
        assertThat(sourceSet.allJava.displayName, equalTo('set name Java source'))
        assertThat(sourceSet.allJava.toString(), equalTo('set name Java source'))
        assertThat(sourceSet.allJava.sourceTrees, not(isEmpty()))

        assertThat(sourceSet.compileTaskName, equalTo('compileSetName'))
        assertThat(sourceSet.compileJavaTaskName, equalTo('compileSetNameJava'))
        assertThat(sourceSet.processResourcesTaskName, equalTo('processSetNameResources'))
    }
    
    @Test public void mainSourceSetUsesSpecialCaseTaskNames() {
        SourceSet sourceSet = new DefaultSourceSet('main', fileResolver, taskResolver)

        assertThat(sourceSet.compileTaskName, equalTo('compile'))
        assertThat(sourceSet.compileJavaTaskName, equalTo('compileJava'))
        assertThat(sourceSet.processResourcesTaskName, equalTo('processResources'))
    }

    @Test public void canConfigureResources() {
        SourceSet sourceSet = new DefaultSourceSet('main', fileResolver, taskResolver)
        sourceSet.resources { srcDir 'src/resources' }
        assertThat(sourceSet.resources.srcDirs, equalTo([new File('src/resources')] as Set))
    }
    
    @Test public void canConfigureJavaSource() {
        SourceSet sourceSet = new DefaultSourceSet('main', fileResolver, taskResolver)
        sourceSet.java { srcDir 'src/java' }
        assertThat(sourceSet.java.srcDirs, equalTo([new File('src/java')] as Set))
    }

    @Test
    public void classesCollectionTracksChangesToClassesDir() {
        SourceSet sourceSet = new DefaultSourceSet('set-name', fileResolver, taskResolver)
        assertThat(sourceSet.classes.files, isEmpty())

        sourceSet.classesDir = new File('classes')
        assertThat(sourceSet.classes.files, equalTo([new File('classes')] as Set))
        sourceSet.classesDir = new File('other-classes')
        assertThat(sourceSet.classes.files, equalTo([new File('other-classes')] as Set))
    }

    @Test
    public void classesCollectionDependenciesTrackChangesToCompileTasks() {
        SourceSet sourceSet = new DefaultSourceSet('set-name', fileResolver, taskResolver)
        assertThat(sourceSet.classes.buildDependencies.getDependencies(null), isEmpty())

        sourceSet.classesDir = new File('classes')
        sourceSet.compiledBy('a', 'b')
        assertThat(sourceSet.classes.buildDependencies.getDependencies(null)*.name as Set, equalTo(['a', 'b'] as Set))
    }
}
