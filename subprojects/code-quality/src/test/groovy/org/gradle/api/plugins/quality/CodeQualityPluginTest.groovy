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
import org.gradle.api.plugins.JavaBasePlugin

class CodeQualityPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final CodeQualityPlugin plugin = new CodeQualityPlugin()

    @Test public void appliesReportingBasePlugin() {
        plugin.apply(project)

        assertTrue(project.plugins.hasPlugin(ReportingBasePlugin))
    }

    @Test public void addsConventionObjectsToProject() {
        plugin.apply(project)

        assertThat(project.convention.plugins.javaCodeQuality, instanceOf(JavaCodeQualityPluginConvention))
        assertThat(project.convention.plugins.groovyCodeQuality, instanceOf(GroovyCodeQualityPluginConvention))
    }

    @Test public void createsTasksAndAppliesMappingsForEachJavaSourceSet() {
        plugin.apply(project)

        project.plugins.apply(JavaPlugin)
        project.checkstyleProperties.someProp = 'someValue'

        def task = project.tasks[CodeQualityPlugin.CHECKSTYLE_MAIN_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.defaultSource, equalTo(project.sourceSets.main.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/main.xml")))
        assertThat(task.properties, equalTo(project.checkstyleProperties))
        assertThat(task, dependsOn())

        task = project.tasks[CodeQualityPlugin.CHECKSTYLE_TEST_TASK]
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.defaultSource, equalTo(project.sourceSets.test.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/test.xml")))
        assertThat(task.properties, equalTo(project.checkstyleProperties))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))

        project.sourceSets.add('custom')
        task = project.tasks['checkstyleCustom']
        assertThat(task, instanceOf(Checkstyle))
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.allJava))
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, equalTo(project.file("build/checkstyle/custom.xml")))
        assertThat(task.properties, equalTo(project.checkstyleProperties))
        assertThat(task, dependsOn())

        task = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
        assertThat(task, dependsOn(hasItems(CodeQualityPlugin.CHECKSTYLE_MAIN_TASK, CodeQualityPlugin.CHECKSTYLE_TEST_TASK, 'checkstyleCustom')))
    }

    @Test public void createsTasksAndAppliesMappingsForEachGroovySourceSet() {
        plugin.apply(project)

        project.plugins.apply(GroovyPlugin)

        def task = project.tasks[CodeQualityPlugin.CODE_NARC_MAIN_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.defaultSource, equalTo(project.sourceSets.main.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/main.html")))
        assertThat(task, dependsOn())

        task = project.tasks[CodeQualityPlugin.CODE_NARC_TEST_TASK]
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.defaultSource, equalTo(project.sourceSets.test.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFormat, equalTo(project.codeNarcReportsFormat))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/test.html")))
        assertThat(task, dependsOn())

        project.sourceSets.add('custom')
        task = project.tasks['codenarcCustom']
        assertThat(task, instanceOf(CodeNarc))
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.allGroovy))
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFormat, equalTo(project.codeNarcReportsFormat))
        assertThat(task.reportFile, equalTo(project.file("build/reports/codenarc/custom.html")))
        assertThat(task, dependsOn())

        task = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
        assertThat(task, dependsOn(hasItem(CodeQualityPlugin.CODE_NARC_MAIN_TASK)))
        assertThat(task, dependsOn(hasItem(CodeQualityPlugin.CODE_NARC_TEST_TASK)))
        assertThat(task, dependsOn(hasItem('codenarcCustom')))
    }

    @Test public void configuresAdditionalTasksDefinedByTheBuildScript() {
        plugin.apply(project)

        def task = project.tasks.add('customCheckstyle', Checkstyle)
        assertThat(task.source, isEmpty())
        assertThat(task.configFile, equalTo(project.checkstyleConfigFile))
        assertThat(task.resultFile, nullValue())
        assertThat(task.classpath, nullValue())

        task = project.tasks.add('customCodeNarc', CodeNarc)
        assertThat(task.source, isEmpty())
        assertThat(task.configFile, equalTo(project.codeNarcConfigFile))
        assertThat(task.reportFile, nullValue())
    }
}
