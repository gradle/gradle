package org.gradle.api.plugins.quality

import org.gradle.api.Project
import org.junit.Test
import org.gradle.api.Task
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.WrapUtil.*
import org.gradle.util.HelperUtil
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.hamcrest.Matcher
import org.gradle.api.DefaultTask

class CodeQualityPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final CodeQualityPlugin plugin = new CodeQualityPlugin()

    @Test public void appliesReportingBasePlugin() {
        plugin.use(project, project.plugins)

        assertTrue(project.plugins.hasPlugin(ReportingBasePlugin))
    }

    @Test public void addsCheckTaskToProject() {
        plugin.use(project, project.plugins)

        def task = project.tasks[CodeQualityPlugin.CHECK_TASK]
        assertThat(task, instanceOf(DefaultTask))
    }

    @Test public void addsConventionObjectWhenJavaPluginApplied() {
        plugin.use(project, project.getPlugins())

        project.usePlugin(JavaPlugin)

        assertThat(project.convention.plugins.javaCodeQuality, instanceOf(JavaCodeQualityPluginConvention))
    }

    @Test public void createsTasksAndAppliesMappingsWhenJavaPluginApplied() {
        plugin.use(project, project.plugins)

        project.usePlugin(JavaPlugin)

        def task = project.tasks[CodeQualityPlugin.CHECKSTYLE_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.allJavaSrc))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.checkstyleResultFile))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CHECKSTYLE_TESTS_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.allJavaTestSrc))
        assertThat(task.configFile, equalTo(project.checkstyleTestConfigFile))
        assertThat(task.resultFile, equalTo(project.checkstyleTestResultFile))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CHECK_TASK]
        assertDependsOn(task, CodeQualityPlugin.CHECKSTYLE_TASK, CodeQualityPlugin.CHECKSTYLE_TESTS_TASK)

        task = project.tasks[JavaPlugin.BUILD_TASK_NAME]
        assertDependsOn(task, hasItem(CodeQualityPlugin.CHECK_TASK))
    }

    @Test public void addsConventionObjectWhenGroovyPluginApplied() {
        plugin.use(project, project.plugins)

        project.usePlugin(GroovyPlugin)

        assertThat(project.convention.plugins.groovyCodeQuality, instanceOf(GroovyCodeQualityPluginConvention))
    }

    @Test public void createsTasksAndAppliesMappingsWhenGroovyPluginApplied() {
        plugin.use(project, project.plugins)

        project.usePlugin(GroovyPlugin)

        def task = project.tasks[CodeQualityPlugin.CODE_NARC_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.allGroovySrc))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.codeNarcReportFile))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CODE_NARC_TESTS_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.allGroovyTestSrc))
        assertThat(task.configFile, equalTo(project.codeNarcTestConfigFile))
        assertThat(task.reportFile, equalTo(project.codeNarcTestReportFile))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CHECK_TASK]
        assertDependsOn(task, hasItem(CodeQualityPlugin.CODE_NARC_TASK))
        assertDependsOn(task, hasItem(CodeQualityPlugin.CODE_NARC_TESTS_TASK))
    }

    private def assertDependsOn(Task task, String... names) {
        assertDependsOn(task, equalTo(toSet(names)))
    }

    private def assertDependsOn(Task task, Matcher<Set<String>> matcher) {
        assertThat(task.taskDependencies.getDependencies(task)*.name as Set, matcher)
    }
}
