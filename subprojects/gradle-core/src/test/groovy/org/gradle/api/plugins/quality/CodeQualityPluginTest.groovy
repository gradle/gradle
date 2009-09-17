package org.gradle.api.plugins.quality

import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class CodeQualityPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final CodeQualityPlugin plugin = new CodeQualityPlugin()

    @Test public void appliesReportingBasePlugin() {
        plugin.use(project, project.plugins)

        assertTrue(project.plugins.hasPlugin(ReportingBasePlugin))
    }

    @Test public void addsConventionObjectsToProject() {
        plugin.use(project, project.getPlugins())

        assertThat(project.convention.plugins.javaCodeQuality, instanceOf(JavaCodeQualityPluginConvention))
        assertThat(project.convention.plugins.groovyCodeQuality, instanceOf(GroovyCodeQualityPluginConvention))
    }

    @Test public void createsTasksAndAppliesMappingsForEachJavaSourceSet() {
        plugin.use(project, project.plugins)

        project.usePlugin(JavaPlugin)

        def task = project.tasks[CodeQualityPlugin.CHECKSTYLE_MAIN_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.source.main.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/main.xml")))
        assertThat(task, dependsOn())

        task = project.tasks[CodeQualityPlugin.CHECKSTYLE_TEST_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.source.test.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/test.xml")))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_TASK_NAME))

        project.source.add('custom')
        task = project.tasks['checkstyleCustom']
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.source, equalTo(project.source.custom.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/custom.xml")))
        assertThat(task, dependsOn())

        task = project.tasks[JavaPlugin.CHECK_TASK_NAME]
        assertThat(task, dependsOn(hasItems(CodeQualityPlugin.CHECKSTYLE_MAIN_TASK, CodeQualityPlugin.CHECKSTYLE_TEST_TASK, 'checkstyleCustom')))
    }

    @Test public void createsTasksAndAppliesMappingsForEachGroovySourceSet() {
        plugin.use(project, project.plugins)

        project.usePlugin(GroovyPlugin)

        def task = project.tasks[CodeQualityPlugin.CODE_NARC_MAIN_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.source.main.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/main.html")))
        assertThat(task, dependsOn())

        task = project.tasks[CodeQualityPlugin.CODE_NARC_TEST_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.source.test.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/test.html")))
        assertThat(task, dependsOn())

        project.source.add('custom')
        task = project.tasks['codenarcCustom']
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.source, equalTo(project.source.custom.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/custom.html")))
        assertThat(task, dependsOn())

        task = project.tasks[JavaPlugin.CHECK_TASK_NAME]
        assertThat(task, dependsOn(hasItem(CodeQualityPlugin.CODE_NARC_MAIN_TASK)))
        assertThat(task, dependsOn(hasItem(CodeQualityPlugin.CODE_NARC_TEST_TASK)))
        assertThat(task, dependsOn(hasItem('codenarcCustom')))
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
}
