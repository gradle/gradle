package org.gradle.api.plugins.quality

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.*
import org.gradle.util.GUtil
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.GroovySourceSet

/**
 * A {@link Plugin} which measures and enforces code quality for Java and Groovy projects.
 */
public class CodeQualityPlugin implements Plugin {
    public static String CHECKSTYLE_MAIN_TASK = "checkstyleMain";
    public static String CHECKSTYLE_TEST_TASK = "checkstyleTest";
    public static String CODE_NARC_MAIN_TASK = "codenarcMain";
    public static String CODE_NARC_TEST_TASK = "codenarcTest";

    public void use(final Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.usePlugin(ReportingBasePlugin.class, project);

        JavaCodeQualityPluginConvention javaPluginConvention = new JavaCodeQualityPluginConvention(project)
        project.convention.plugins.javaCodeQuality = javaPluginConvention;

        GroovyCodeQualityPluginConvention groovyPluginConvention = new GroovyCodeQualityPluginConvention(project)
        project.convention.plugins.groovyCodeQuality = groovyPluginConvention;

        configureCheckstyleDefaults(project, javaPluginConvention)
        configureCodeNarcDefaults(project, groovyPluginConvention)

        project.plugins.withType(JavaPlugin.class).allPlugins {
            configureForJavaPlugin(project, javaPluginConvention);
        }
        project.plugins.withType(GroovyPlugin.class).allPlugins {
            configureForGroovyPlugin(project, groovyPluginConvention);
        }
    }

    private void configureCheckstyleDefaults(Project project, JavaCodeQualityPluginConvention pluginConvention) {
        project.tasks.withType(Checkstyle.class).allTasks {Checkstyle checkstyle ->
            checkstyle.conventionMapping.configFile = { pluginConvention.checkstyleConfigFile }
            checkstyle.conventionMapping.map('properties') { pluginConvention.checkstyleProperties }
        }
    }

    private void configureCodeNarcDefaults(Project project, GroovyCodeQualityPluginConvention pluginConvention) {
        project.tasks.withType(CodeNarc.class).allTasks {CodeNarc codenarc ->
            codenarc.conventionMapping.configFile = { pluginConvention.codeNarcConfigFile }
        }
    }

    private void configureCheckTask(Project project) {
        Task task = project.tasks[JavaPlugin.CHECK_TASK_NAME]
        task.setDescription("Executes all quality checks");
        task.dependsOn { project.tasks.withType(Checkstyle.class).all; }
        task.dependsOn { project.tasks.withType(CodeNarc.class).all; }
    }

    private void configureForJavaPlugin(Project project, JavaCodeQualityPluginConvention pluginConvention) {
        configureCheckTask(project);

        project.convention.getPlugin(JavaPluginConvention.class).sourceSets.allObjects {SourceSet set ->
            Checkstyle checkstyle = project.tasks.add("checkstyle${GUtil.toCamelCase(set.name)}", Checkstyle.class);
            checkstyle.description = "Runs Checkstyle against the $set.name Java source code."
            checkstyle.conventionMapping.defaultSource = { set.allJava; }
            checkstyle.conventionMapping.configFile = { pluginConvention.checkstyleConfigFile }
            checkstyle.conventionMapping.resultFile = { new File(pluginConvention.checkstyleResultsDir, "${set.name}.xml") }
            checkstyle.conventionMapping.classpath = { set.compileClasspath; }
        }
    }

    private void configureForGroovyPlugin(Project project, GroovyCodeQualityPluginConvention pluginConvention) {
        project.convention.getPlugin(JavaPluginConvention.class).sourceSets.allObjects {SourceSet set ->
            GroovySourceSet groovySourceSet = set.convention.getPlugin(GroovySourceSet.class)
            CodeNarc codeNarc = project.tasks.add("codenarc${GUtil.toCamelCase(set.name)}", CodeNarc.class);
            codeNarc.setDescription("Runs CodeNarc against the $set.name Groovy source code.");
            codeNarc.conventionMapping.defaultSource = { groovySourceSet.allGroovy; }
            codeNarc.conventionMapping.configFile = { pluginConvention.codeNarcConfigFile; }
            codeNarc.conventionMapping.reportFile = { new File(pluginConvention.codeNarcReportsDir, "${set.name}.html"); }
        }
    }
}
