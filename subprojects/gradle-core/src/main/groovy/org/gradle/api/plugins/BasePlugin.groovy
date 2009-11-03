package org.gradle.api.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Clean
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

/**
 * <p>A {@link org.gradle.api.Plugin} which defines a basic project lifecycle and some common convention properties.</p>
 */
class BasePlugin implements Plugin {
    public static final String CLEAN_TASK_NAME = "clean"
    public static final String ASSEMBLE_TASK_NAME = "assemble"

    public void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        project.convention.plugins.base = new BasePluginConvention(project)

        configureBuildConfigurationRule(project)
        configureUploadRules(project)
        configureArchiveDefaults(project, project.convention.plugins.base)

        addClean(project)
        addAssemble(project);
    }

    private Task addAssemble(Project project) {
        Task assembleTask = project.tasks.add(ASSEMBLE_TASK_NAME);
        assembleTask.description = "Builds all Jar, War, Zip, and Tar archives.";
        assembleTask.dependsOn { project.tasks.withType(AbstractArchiveTask.class).all }
    }

    private void configureArchiveDefaults(Project project, BasePluginConvention pluginConvention) {
        project.tasks.withType(AbstractArchiveTask).allTasks { AbstractArchiveTask task ->
            if (task instanceof Jar) {
                task.conventionMapping.destinationDir = { pluginConvention.libsDir }
            } else {
                task.conventionMapping.destinationDir = { pluginConvention.distsDir }
            }
            task.conventionMapping.version = { project.version == Project.DEFAULT_VERSION ? null : project.version.toString() }
            task.conventionMapping.baseName = { pluginConvention.archivesBaseName }
        }
    }

    private void addClean(final Project project) {
        Clean clean = project.tasks.add(CLEAN_TASK_NAME, Clean.class)
        clean.description = "Deletes the build directory.";
        clean.conventionMapping.dir = { project.buildDir }
    }

    private void configureBuildConfigurationRule(final Project project) {
        final String prefix = "build";
        Rule rule = [
                getDescription: {
                    String.format("Pattern: %s<ConfigurationName>: Builds the artifacts belonging to the configuration.", prefix)
                },
                apply: {String taskName ->
                    if (taskName.startsWith(prefix)) {
                        Configuration configuration = project.configurations.findByName(taskName.substring(prefix.length()).toLowerCase())
                        if (configuration != null) {
                            project.tasks.add(taskName).dependsOn(configuration.getBuildArtifacts()).setDescription(String.format("Builds the artifacts belonging to %s.", configuration))
                        }
                    }
                }
        ] as Rule

        project.configurations.allObjects {
            if (!project.tasks.rules.contains(rule)) {
                project.tasks.addRule(rule)
            }
        }
    }

    private void configureUploadRules(final Project project) {
        Rule rule = [
                getDescription: {
                    "Pattern: upload<ConfigurationName>: Uploads the project artifacts of a configuration to a public Gradle repository."
                },
                apply: {String taskName ->
                    Set<Configuration> configurations = project.configurations.all
                    for (Configuration configuration: configurations) {
                        if (taskName.equals(configuration.uploadTaskName)) {
                            createUploadTask(configuration.uploadTaskName, configuration, project)
                        }
                    }
                }
        ] as Rule

        project.configurations.allObjects {
            if (!project.tasks.rules.contains(rule)) {
                project.tasks.addRule(rule)
            }
        }
    }

    private Upload createUploadTask(String name, final Configuration configuration, Project project) {
        Upload upload = project.getTasks().add(name, Upload.class)
        upload.configuration = configuration
        upload.uploadDescriptor = true
        upload.descriptorDestination = new File(project.getBuildDir(), "ivy.xml")
        upload.description = String.format("Uploads all artifacts belonging to %s.", configuration)
        return upload
    }
    
}
