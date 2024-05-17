package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

// tag::snippet[]
public class DataProcessingPlugin implements Plugin<Project> {
    public void apply(Project project) {
        Configuration dataFiles = project.getConfigurations().create("dataFiles", c -> {
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setDescription("The data artifacts to be processed for this plugin.");
            c.defaultDependencies(d -> d.add(project.getDependencies().create("org.myorg:data:1.4.6")));
        });

        project.getTasks().withType(DataProcessing.class).configureEach(
            dataProcessing -> dataProcessing.getDataFiles().from(dataFiles));
    }
}
// end::snippet[]
