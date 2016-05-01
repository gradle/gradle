package org.gradle.api.plugins;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.distribution.internal.DefaultDistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.bundling.Jar;

/**
 * A {@link Plugin} which package a Java project as a distribution including the JAR and runtime dependencies.
 */
@Incubating
public class JavaLibraryDistributionPlugin implements Plugin<ProjectInternal> {
    private Project project;

    public void apply(final ProjectInternal project) {
        this.project = project;
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(DistributionPlugin.class);

        DefaultDistributionContainer defaultDistributionContainer =
            project.getConvention().getPlugin(DefaultDistributionContainer.class);
        CopySpec contentSpec = defaultDistributionContainer.getByName(DistributionPlugin.MAIN_DISTRIBUTION_NAME).getContents();
        Jar jar = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
        contentSpec.from(jar);
        contentSpec.from(project.file("src/dist"));
        contentSpec.into("lib").from(project.getConfigurations().getByName("runtime"));
    }
}
