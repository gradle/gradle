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

    @Test public void addsConventionObjectsToProject() {
        plugin.use(project, project.getPlugins())

        assertThat(project.convention.plugins.javaCodeQuality, instanceOf(JavaCodeQualityPluginConvention))
        assertThat(project.convention.plugins.groovyCodeQuality, instanceOf(GroovyCodeQualityPluginConvention))
    }

    @Test public void attachesCheckTaskToBuildTaskWhenJavaPluginApplied() {
        plugin.use(project, project.getPlugins())

        project.usePlugin(JavaPlugin)

        def task = project.tasks[JavaPlugin.BUILD_TASK_NAME]
        assertDependsOn(task, hasItem(CodeQualityPlugin.CHECK_TASK))
    }

    @Test public void createsTasksAndAppliesMappingsForEachJavaSourceSet() {
        plugin.use(project, project.plugins)

        project.usePlugin(JavaPlugin)

        def task = project.tasks[CodeQualityPlugin.CHECKSTYLE_MAIN_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.source.main.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/main.xml")))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CHECKSTYLE_TEST_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.source.test.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/test.xml")))
        assertDependsOn(task)

        project.source.add('custom')
        task = project.tasks['checkstyleCustom']
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.source.custom.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/custom.xml")))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CHECK_TASK]
        assertDependsOn(task, CodeQualityPlugin.CHECKSTYLE_MAIN_TASK, CodeQualityPlugin.CHECKSTYLE_TEST_TASK, 'checkstyleCustom')
    }

    @Test public void createsTasksAndAppliesMappingsForEachGroovySourceSet() {
        plugin.use(project, project.plugins)

        project.usePlugin(GroovyPlugin)

        def task = project.tasks[CodeQualityPlugin.CODE_NARC_MAIN_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.source.main.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/main.html")))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CODE_NARC_TEST_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.source.test.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/test.html")))
        assertDependsOn(task)

        project.source.add('custom')
        task = project.tasks['codenarcCustom']
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.source.custom.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/custom.html")))
        assertDependsOn(task)

        task = project.tasks[CodeQualityPlugin.CHECK_TASK]
        assertDependsOn(task, hasItem(CodeQualityPlugin.CODE_NARC_MAIN_TASK))
        assertDependsOn(task, hasItem(CodeQualityPlugin.CODE_NARC_TEST_TASK))
        assertDependsOn(task, hasItem('codenarcCustom'))
    }

    @Test public void configuresAdditionalTasksDefinedByTheBuildScript() {
        plugin.use(project, project.plugins)

        def task = project.tasks.add('customCheckstyle', Checkstyle)
        assertThat(task.source, nullValue())
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, nullValue())
        assertThat(task.classpath, nullValue())

        task = project.tasks.add('customCodeNarc', CodeNarc)
        assertThat(task.source, nullValue())
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, nullValue())
    }

    private def assertDependsOn(Task task, String... names) {
        assertDependsOn(task, equalTo(toSet(names)))
    }

    private def assertDependsOn(Task task, Matcher<Set<String>> matcher) {
        assertThat(task.taskDependencies.getDependencies(task)*.name as Set, matcher)
    }
}
