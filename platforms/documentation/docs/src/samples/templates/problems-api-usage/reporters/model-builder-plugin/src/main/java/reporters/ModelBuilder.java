package reporters;

import org.gradle.api.Project;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import javax.inject.Inject;

public class ModelBuilder implements ToolingModelBuilder {

    private final Problems problems;

    @Inject
    public ModelBuilder(Problems problems) {
        this.problems = problems;
    }

    @Override
    public boolean canBuild(String modelName) {
        return DemoModel.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        problems.getReporter().report(ProblemId.create("unused", "Demo model", ModelBuilderPlugin.PROBLEM_GROUP), problem -> problem
            .severity(Severity.WARNING)
            .details("This is a demo model and doesn't do anything useful")
        );
        return new DefaultDemoModel();
    }
}
