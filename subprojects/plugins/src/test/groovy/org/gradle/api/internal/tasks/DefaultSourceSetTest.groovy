/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class DefaultSourceSetTest {
    public @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private final TaskResolver taskResolver = [resolveTask: {name -> [getName: {name}] as Task}] as TaskResolver
    private final FileResolver fileResolver = TestFiles.resolver(tmpDir.testDirectory)

    private DefaultSourceSet sourceSet(String name) {
        def s = new DefaultSourceSet(name, TestFiles.sourceDirectorySetFactory(tmpDir.testDirectory))
        s.classes = new DefaultSourceSetOutput(s.displayName, fileResolver, taskResolver)
        return s
    }

    @Before
    public void setup() {
        NativeServicesTestFixture.initialize()
    }

    @Test
    public void hasUsefulDisplayName() {
        SourceSet sourceSet = sourceSet('int-test')
        assertThat(sourceSet.toString(), equalTo("source set 'int test'"));
    }

    @Test public void defaultValues() {
        SourceSet sourceSet = sourceSet('set-name')

        assertThat(sourceSet.output.classesDir, nullValue())
        assertThat(sourceSet.output.files, isEmpty())
        assertThat(sourceSet.output.displayName, equalTo('set name classes'))
        assertThat(sourceSet.output.toString(), equalTo('set name classes'))
        assertThat(sourceSet.output.buildDependencies.getDependencies(null), isEmpty())

        assertThat(sourceSet.output.classesDir, nullValue())
        assertThat(sourceSet.output.resourcesDir, nullValue())

        assertThat(sourceSet.compileClasspath, nullValue())

        assertThat(sourceSet.runtimeClasspath, nullValue())

        assertThat(sourceSet.resources, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.resources, isEmpty())
        assertThat(sourceSet.resources.displayName, equalTo('set name resources'))
        assertThat(sourceSet.resources.toString(), equalTo('set name resources'))

        assertThat(sourceSet.resources.filter.includes, isEmpty())
        assertThat(sourceSet.resources.filter.excludes, isEmpty())

        assertThat(sourceSet.java, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.java, isEmpty())
        assertThat(sourceSet.java.displayName, equalTo('set name Java source'))
        assertThat(sourceSet.java.toString(), equalTo('set name Java source'))

        assertThat(sourceSet.java.filter.includes, equalTo(['**/*.java'] as Set))
        assertThat(sourceSet.java.filter.excludes, isEmpty())

        assertThat(sourceSet.allJava, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.allJava, isEmpty())
        assertThat(sourceSet.allJava.displayName, equalTo('set name Java source'))
        assertThat(sourceSet.allJava.toString(), equalTo('set name Java source'))
        assertThat(sourceSet.allJava.source, hasItem(sourceSet.java))
        assertThat(sourceSet.allJava.filter.includes, equalTo(['**/*.java'] as Set))
        assertThat(sourceSet.allJava.filter.excludes, isEmpty())

        assertThat(sourceSet.allSource, instanceOf(DefaultSourceDirectorySet))
        assertThat(sourceSet.allSource, isEmpty())
        assertThat(sourceSet.allSource.displayName, equalTo('set name source'))
        assertThat(sourceSet.allSource.toString(), equalTo('set name source'))
        assertThat(sourceSet.allSource.source, hasItem(sourceSet.java))
    }

    @Test public void constructsNamesUsingSourceSetName() {
        SourceSet sourceSet = sourceSet('set-name')

        assertThat(sourceSet.classesTaskName, equalTo('setNameClasses'))
        assertThat(sourceSet.getCompileTaskName('java'), equalTo('compileSetNameJava'))
        assertThat(sourceSet.compileJavaTaskName, equalTo('compileSetNameJava'))
        assertThat(sourceSet.processResourcesTaskName, equalTo('processSetNameResources'))
        assertThat(sourceSet.jarTaskName, equalTo('setNameJar'))
        assertThat(sourceSet.getTaskName('build', null), equalTo('buildSetName'))
        assertThat(sourceSet.getTaskName(null, 'jar'), equalTo('setNameJar'))
        assertThat(sourceSet.compileConfigurationName, equalTo("setNameCompile"))
        assertThat(sourceSet.runtimeConfigurationName, equalTo("setNameRuntime"))
        assertThat(sourceSet.compileOnlyConfigurationName, equalTo("setNameCompileOnly"))
        assertThat(sourceSet.compileClasspathConfigurationName, equalTo("setNameCompileClasspath"))
        assertThat(sourceSet.apiConfigurationName, equalTo("setNameApi"))
        assertThat(sourceSet.apiElementsConfigurationName, equalTo("setNameApiElements"))
    }

    @Test public void mainSourceSetUsesSpecialCaseNames() {
        SourceSet sourceSet = sourceSet('main')

        assertThat(sourceSet.classesTaskName, equalTo('classes'))
        assertThat(sourceSet.getCompileTaskName('java'), equalTo('compileJava'))
        assertThat(sourceSet.compileJavaTaskName, equalTo('compileJava'))
        assertThat(sourceSet.processResourcesTaskName, equalTo('processResources'))
        assertThat(sourceSet.jarTaskName, equalTo('jar'))
        assertThat(sourceSet.getTaskName('build', null), equalTo('buildMain'))
        assertThat(sourceSet.getTaskName(null, 'jar'), equalTo('jar'))
        assertThat(sourceSet.getTaskName('build', 'jar'), equalTo('buildJar'))
        assertThat(sourceSet.compileConfigurationName, equalTo("compile"))
        assertThat(sourceSet.runtimeConfigurationName, equalTo("runtime"))
        assertThat(sourceSet.compileOnlyConfigurationName, equalTo("compileOnly"))
        assertThat(sourceSet.compileClasspathConfigurationName, equalTo("compileClasspath"))
        assertThat(sourceSet.apiConfigurationName, equalTo("api"))
        assertThat(sourceSet.apiElementsConfigurationName, equalTo("apiElements"))
    }

    @Test public void canConfigureResources() {
        SourceSet sourceSet = sourceSet('main')
        sourceSet.resources { srcDir 'src/resources' }
        assertThat(sourceSet.resources.srcDirs, equalTo([tmpDir.file('src/resources')] as Set))
    }

    @Test public void canConfigureResourcesUsingAnAction() {
        SourceSet sourceSet = sourceSet('main')
        sourceSet.resources({ set -> set.srcDir 'src/resources' } as Action<SourceDirectorySet>)
        assertThat(sourceSet.resources.srcDirs, equalTo([tmpDir.file('src/resources')] as Set))
    }

    @Test public void canConfigureJavaSource() {
        SourceSet sourceSet = sourceSet('main')
        sourceSet.java { srcDir 'src/java' }
        assertThat(sourceSet.java.srcDirs, equalTo([tmpDir.file('src/java')] as Set))
    }

    @Test public void canConfigureJavaSourceUsingAnAction() {
        SourceSet sourceSet = sourceSet('main')
        sourceSet.java({ set -> set.srcDir 'src/java' } as Action<SourceDirectorySet>)
        assertThat(sourceSet.java.srcDirs, equalTo([tmpDir.file('src/java')] as Set))
    }

    @Test
    public void tracksChangesToClassesDir() {
        SourceSet sourceSet = sourceSet('set-name')
        assertThat(sourceSet.output.files, isEmpty())

        def dir1 = tmpDir.file('classes')
        def dir2 = tmpDir.file('other-classes')

        sourceSet.output.classesDir = dir1
        assertThat(sourceSet.output.files, equalTo([dir1] as Set))

        sourceSet.output.classesDir = dir2
        assertThat(sourceSet.output.files, equalTo([dir2] as Set))
    }

    @Test
    public void dependenciesTrackChangesToCompileTasks() {
        SourceSet sourceSet = sourceSet('set-name')
        sourceSet.output.classesDir = new File('classes')

        def dependencies = sourceSet.output.buildDependencies
        assertThat(dependencies.getDependencies(null), isEmpty())

        sourceSet.compiledBy('a', 'b')
        assertThat(dependencies.getDependencies(null)*.name as Set, equalTo(['a', 'b'] as Set))

        sourceSet.compiledBy('c')
        assertThat(dependencies.getDependencies(null)*.name as Set, equalTo(['a', 'b', 'c'] as Set))
    }

    @Test
    public void dependenciesTrackChangesToOutputDirs() {
        SourceSet sourceSet = sourceSet('set-name')
        sourceSet.output.classesDir = new File('classes')

        def dependencies = sourceSet.output.buildDependencies
        assertThat(dependencies.getDependencies(null), isEmpty())

        sourceSet.compiledBy('a')

        def dirs1 = new DefaultConfigurableFileCollection(fileResolver, taskResolver)

        dirs1.builtBy('b')
        sourceSet.output.dir(dirs1)
        assertThat(dependencies.getDependencies(null)*.name as Set, equalTo(['a', 'b'] as Set))

        dirs1.builtBy('c')
        assertThat(dependencies.getDependencies(null)*.name as Set, equalTo(['a', 'b', 'c'] as Set))
    }
}
