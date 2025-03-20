package org.gradle.sample;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.Problems;

import javax.inject.Inject;

public abstract class SamplePlugin implements Plugin<Project> {

    @Inject
    public abstract Problems getProblems();

    @Override
    public void apply(Project project) {
        getProblems().getDeprecationReporter().deprecatePlugin(
            "org.gradle.sample.plugin",
            spec -> spec
                .removedInVersion(2, 0, 0, null)
                .replacedBy("org.gradle.sample.newer-plugin")
                .because("Plugin was renamed")
                .withDetails("""
                    We decided to rename the plugin to better reflect its purpose.
                    You can find the new plugin at https://plugins.gradle.org/org.gradle.sample.newer-plugin
                """)
        );

        project.getExtensions().create("sampleExtension", SampleExtension.class);
    }

}
