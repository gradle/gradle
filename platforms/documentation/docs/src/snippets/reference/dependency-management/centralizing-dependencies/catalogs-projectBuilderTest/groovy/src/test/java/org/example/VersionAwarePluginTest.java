package org.example;

import org.gradle.api.Project;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VersionAwarePluginTest {

    // tag::project_builder_version_catalog_test[]
    @Test
    public void readsLibraryFromFakeCatalog() {
        Project project = ProjectBuilder.builder().build();

        // MinimalExternalModuleDependency and its collaborators are plain interfaces --
        // Mockito is the pragmatic way to stub just the methods a plugin actually calls.
        ModuleIdentifier module = mock(ModuleIdentifier.class);
        when(module.getGroup()).thenReturn("com.google.guava");
        when(module.getName()).thenReturn("guava");

        VersionConstraint versionConstraint = mock(VersionConstraint.class);
        when(versionConstraint.getRequiredVersion()).thenReturn("33.4.0-jre");

        MinimalExternalModuleDependency guavaDependency = mock(MinimalExternalModuleDependency.class);
        when(guavaDependency.getModule()).thenReturn(module);
        when(guavaDependency.getVersionConstraint()).thenReturn(versionConstraint);

        VersionCatalog fakeLibs = new FakeVersionCatalog("libs", Collections.singletonMap(
            "guava", project.getProviders().provider(() -> guavaDependency)
        ));

        // Register the fake catalog *before* applying the plugin, and never call anything that
        // forces project evaluation (such as Project.getTasksByName()). Doing so triggers Gradle's
        // automatic "versionCatalogs" extension registration, which creates an *empty* catalog
        // that cannot be replaced afterwards -- see the linked issue for the exact failure.
        project.getExtensions().add(VersionCatalogsExtension.class, "versionCatalogs", new FakeVersionCatalogsExtension(fakeLibs));

        project.getPluginManager().apply(VersionAwarePlugin.class);

        String description = project.getTasks().getByName("printGuavaVersion").getDescription();
        assertEquals(
            "Prints the resolved guava dependency notation: com.google.guava:guava:33.4.0-jre",
            description
        );
    }
    // end::project_builder_version_catalog_test[]
}
