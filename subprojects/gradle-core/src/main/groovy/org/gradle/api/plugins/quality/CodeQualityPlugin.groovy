package org.gradle.api.plugins.quality

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.*

/**
 * A  {@link Plugin}  which measures and enforces code quality for Java projects.
 */
public class CodeQualityPlugin implements Plugin {
    public static String CHECKSTYLE_TASK = "checkstyle";
    public static String CHECKSTYLE_TESTS_TASK = "checkstyleTests";
    public static String CODE_NARC_TASK = "codenarc";
    public static String CODE_NARC_TESTS_TASK = "codenarcTests";
    public static String CHECK_TASK = "check";

    public void use(final Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.usePlugin(ReportingBasePlugin.class, project);

        configureCheckTask(project);

        project.getPlugins().withType(JavaPlugin.class).allPlugins {Plugin plugin ->
            configureForJavaPlugin(project);
        }
        project.getPlugins().withType(GroovyPlugin.class).allPlugins {Plugin plugin ->
            configureForGroovyPlugin(project);
        }
    }

    private void configureCheckTask(final Project project) {
        Task task = project.getTasks().add(CHECK_TASK);
        task.setDescription("Executes all quality checks");
        project.getTasks().getByName(CHECK_TASK).dependsOn {
            return project.getTasks().withType(Checkstyle.class).getAll();
        }
        project.getTasks().getByName(CHECK_TASK).dependsOn {
            return project.getTasks().withType(CodeNarc.class).getAll();
        }
    }

    private void configureForJavaPlugin(final Project project) {
        project.getConvention().getPlugins().put("javaCodeQuality", new JavaCodeQualityPluginConvention(project));

        Task buildTask = project.getTasks().findByName(JavaPlugin.BUILD_TASK_NAME);
        buildTask.dependsOn(CHECK_TASK);

        project.getTasks().withType(Checkstyle.class).allTasks {Checkstyle checkstyle ->
            checkstyle.conventionMapping.map("source") {Convention convention ->
                return convention.getPlugin(JavaPluginConvention.class).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME).getAllJava();
            }
            checkstyle.conventionMapping.configFile = {Convention convention ->
                return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleConfigFile();
            }
            checkstyle.conventionMapping.classpath = {Convention convention ->
                return convention.getPlugin(JavaPluginConvention.class).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME).getCompileClasspath();
            }
        }

        Checkstyle checkstyle = project.getTasks().add(CHECKSTYLE_TASK, Checkstyle.class);
        checkstyle.setDescription("Runs Checkstyle against the Java source code.");
        checkstyle.conventionMapping.resultFile = {Convention convention ->
            return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleResultFile();
        }

        checkstyle = project.getTasks().add(CHECKSTYLE_TESTS_TASK, Checkstyle.class);
        checkstyle.setDescription("Runs Checkstyle against the Java test source code.");
        checkstyle.conventionMapping.map("source") {Convention convention ->
            return convention.getPlugin(JavaPluginConvention.class).getSource().getByName(JavaPlugin.TEST_SOURCE_SET_NAME).getAllJava();
        }
        checkstyle.conventionMapping.classpath = {Convention convention ->
            return convention.getPlugin(JavaPluginConvention.class).getSource().getByName(JavaPlugin.TEST_SOURCE_SET_NAME).getCompileClasspath();
        }
        checkstyle.conventionMapping.configFile = {Convention convention ->
            return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleTestConfigFile();
        }
        checkstyle.conventionMapping.resultFile = {Convention convention ->
            return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleTestResultFile();
        }
    }

    private void configureForGroovyPlugin(final Project project) {
        project.getConvention().getPlugins().put("groovyCodeQuality", new GroovyCodeQualityPluginConvention(project));

        project.getTasks().withType(CodeNarc.class).allTasks {CodeNarc codeNarc ->
            codeNarc.conventionMapping.map("source") {Convention convention ->
                return convention.getPlugin(GroovyPluginConvention.class).getAllGroovySrc();
            }
            codeNarc.conventionMapping.configFile = {Convention convention ->
                return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcConfigFile();
            }
        }

        CodeNarc codeNarc = project.getTasks().add(CODE_NARC_TASK, CodeNarc.class);
        codeNarc.setDescription("Runs CodeNarc against the Groovy source code.");
        codeNarc.conventionMapping.reportFile = {Convention convention ->
            return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcReportFile();
        }

        codeNarc = project.getTasks().add(CODE_NARC_TESTS_TASK, CodeNarc.class);
        codeNarc.setDescription("Runs CodeNarc against the Groovy test source code.");
        codeNarc.conventionMapping.map("source") {Convention convention ->
            return convention.getPlugin(GroovyPluginConvention.class).getAllGroovyTestSrc();
        }
        codeNarc.conventionMapping.configFile = {Convention convention ->
            return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcTestConfigFile();
        }
        codeNarc.conventionMapping.reportFile = {Convention convention ->
            return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcTestReportFile();
        }
    }
}
