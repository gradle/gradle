package org.gradle.api.plugins.ant

import org.gradle.api.Project
import org.gradle.api.plugins.ant.AntPlugin
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.api.internal.project.PluginRegistry
import groovy.xml.MarkupBuilder


public class AntPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final AntPlugin antPlugin = new AntPlugin()
    private final File dir = HelperUtil.makeNewTestDir()

    @Test
    public void addsConvention() {
        antPlugin.apply(project, new PluginRegistry(), null)

        assertThat(project.convention.plugins.ant, instanceOf(AntPluginConvention))
    }

    @Test
    public void addsTaskForEachAntTarget() {
        antPlugin.apply(project, new PluginRegistry(), null)

        File buildFile = new File(dir, 'build.xml')
        buildFile.withWriter {Writer writer ->
            def xml = new MarkupBuilder(writer)
            xml.project {
                target(name: 'target1', depends: 'target2, target3')
                target(name: 'target2')
                target(name: 'target3')
            }
        }

        project.importAntBuild(buildFile)

        def task = project.tasks.target1
        assertThat(task, instanceOf(AntTask))
        assertThat(task.target.name, equalTo('target1'))
        
        task = project.tasks.target2
        assertThat(task, instanceOf(AntTask))
        assertThat(task.target.name, equalTo('target2'))

        task = project.tasks.target3
        assertThat(task, instanceOf(AntTask))
        assertThat(task.target.name, equalTo('target3'))
    }
}