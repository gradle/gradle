package org.gradle.sample;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.util.Collection;

// tag::fetch-action[]
public class FetchEclipseModelAction implements BuildAction<EclipseProjectResult> {
    @Override
    public EclipseProjectResult execute(BuildController controller) {
        FetchModelResult<EclipseProject> result = controller.fetch(EclipseProject.class);

        EclipseProject model = result.getModel(); // may be null
        Collection<? extends Failure> failures = result.getFailures();

        return new EclipseProjectResult(model, failures);
    }
}
// end::fetch-action[]
