package org.gradle.sample;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.deprecation.source.ReportSource;

import javax.inject.Inject;

public abstract class SamplePlugin implements Plugin<Project> {

    @Inject
    public abstract Problems getProblems();

    @Override
    public void apply(Project project) {
        getProblems().getDeprecationReporter().deprecatePlugin(
            ReportSource.plugin("org.gradle.sample.plugin"),
            "org.gradle.sample.plugin",
            spec -> spec
                .willBeRemovedInVersion("2.0.0")
                .shouldBeReplacedBy("org.gradle.sample.newer-plugin")
                .details("""
                    We decided to rename the plugin to better reflect its purpose.
                    You can find the new plugin at https://plugins.gradle.org/org.gradle.sample.newer-plugin
                """)
        );

        project.getExtensions().create("sampleExtension", SampleExtension.class);
    }

}
