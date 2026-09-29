package org.example;

// tag::plugin_reads_catalog[]
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;

public class VersionAwarePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        VersionCatalogsExtension catalogs = project.getExtensions().getByType(VersionCatalogsExtension.class);
        VersionCatalog libs = catalogs.named("libs");
        MinimalExternalModuleDependency guava = libs.findLibrary("guava")
            .orElseThrow(() -> new GradleException("No 'guava' library declared in the 'libs' version catalog"))
            .get();

        project.getTasks().create("printGuavaVersion").setDescription(
            "Prints the resolved guava dependency notation: " + notation(guava)
        );
    }

    private static String notation(MinimalExternalModuleDependency dependency) {
        return dependency.getModule().getGroup() + ":" + dependency.getModule().getName() + ":" + dependency.getVersionConstraint().getRequiredVersion();
    }
}
// end::plugin_reads_catalog[]
