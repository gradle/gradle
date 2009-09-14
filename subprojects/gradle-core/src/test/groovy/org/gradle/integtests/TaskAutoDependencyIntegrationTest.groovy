package org.gradle.integtests

import org.junit.Test
import org.junit.Ignore

class TaskAutoDependencyIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void autoAddsInputFileCollectionAsADependency() {
        // Include a configuration with transitive dep on a Jar and an unmanaged Jar.

        testFile('settings.gradle') << 'include "a", "b"'
        testFile('a/build.gradle') << '''
configurations { compile }
dependencies { compile project(path: ':b', configuration: 'archives') }

task doStuff(type: InputTask) {
    src = configurations.compile + fileTree('src/java')
}

class InputTask extends DefaultTask {
    @InputFiles
    def FileCollection src
}
'''
        testFile('b/build.gradle') << '''
usePlugin org.gradle.api.plugins.BasePlugin
task jar << {
    file('b.jar').text = 'some jar'
}

task otherJar(type: Jar) {
    destinationDir = buildDir
}

configurations { archives }
dependencies { archives files('b.jar') { builtBy jar } }
artifacts { archives otherJar }
'''
        inTestDirectory().withTasks('doStuff').run().assertTasksExecuted(':b:jar', ':b:otherJar', ':b:uploadArchivesInternal', ':a:doStuff')
    }

    @Test @Ignore
    public void addsDependenciesForInheritedConfiguration() {
        fail()
    }

    @Test @Ignore
    public void addsDependenciesForFileCollectionInSameProject() {
        fail()
    }
    
    @Test @Ignore
    public void addsDependenciesForFileCollectionInProjectWithNoArtifacts() {
        fail()
    }
}
