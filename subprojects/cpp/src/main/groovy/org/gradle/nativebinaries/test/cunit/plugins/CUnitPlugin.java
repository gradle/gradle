package org.gradle.nativebinaries.test.cunit.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.ProjectNativeComponent;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.test.TestSuiteContainer;
import org.gradle.nativebinaries.test.cunit.CUnitTestSuite;
import org.gradle.nativebinaries.test.cunit.internal.ConfigureCUnitTestSources;
import org.gradle.nativebinaries.test.cunit.internal.CreateCUnitBinaries;
import org.gradle.nativebinaries.test.cunit.internal.DefaultCUnitTestSuite;
import org.gradle.nativebinaries.test.plugins.NativeBinariesTestPlugin;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.NamedProjectComponentIdentifier;
import org.gradle.runtime.base.ProjectComponentContainer;
import org.gradle.runtime.base.internal.DefaultNamedProjectComponentIdentifier;

import javax.inject.Inject;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class CUnitPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;
    private final NativeDependencyResolver resolver;

    @Inject
    public CUnitPlugin(Instantiator instantiator, NativeDependencyResolver resolver) {
        this.instantiator = instantiator;
        this.resolver = resolver;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeBinariesTestPlugin.class);

        final TestSuiteContainer testSuites = project.getExtensions().getByType(TestSuiteContainer.class);
        final BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);
        ProjectComponentContainer components = project.getExtensions().getByType(ProjectComponentContainer.class);

        components.withType(ProjectNativeComponent.class).all(new Action<ProjectNativeComponent>() {
            public void execute(ProjectNativeComponent component) {
                testSuites.add(createCUnitTestSuite(component, binaries, project));
            }
        });
    }

    private CUnitTestSuite createCUnitTestSuite(final ProjectNativeComponent testedComponent, BinaryContainer binaries, ProjectInternal project) {
        String suiteName = String.format("%sTest", testedComponent.getName());
        String path = testedComponent.getProjectPath();
        NamedProjectComponentIdentifier id = new DefaultNamedProjectComponentIdentifier(path, suiteName);
        CUnitTestSuite cUnitTestSuite = instantiator.newInstance(DefaultCUnitTestSuite.class, id, testedComponent);

        new ConfigureCUnitTestSources(project).apply(cUnitTestSuite);
        new CreateCUnitBinaries(project, instantiator, resolver).apply(cUnitTestSuite, binaries);
        return cUnitTestSuite;
    }
}
