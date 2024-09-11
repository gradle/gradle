package org.gradle.sample;

import org.gradle.api.logging.Logging;
import org.gradle.tooling.*;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.problems.*;
import org.slf4j.Logger;
import reporters.DemoModel;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class SampleIde {

    private static final Logger LOGGER = Logging.getLogger(SampleIde.class);
    private final String workingDir;
    private final String taskPath;

    public SampleIde(String workingDir, String taskPath) {
        this.workingDir = workingDir;
        this.taskPath = taskPath;
    }

    private ProjectConnection createGradleConnection() {
        // Get current working directory
        Path projectPath = Path.of(workingDir);
        File projectDir = projectPath.toFile();

        // Initialize the Tooling API
        return GradleConnector.newConnector().useGradleVersion("8.9").forProjectDirectory(projectDir).connect();
    }

    public void buildModel() {
        try (ProjectConnection connection = createGradleConnection()) {
            ModelBuilder<DemoModel> modelBuilder = connection.model(DemoModel.class).addArguments("--quiet");
            ProblemListener.createAndRegister(modelBuilder);
            modelBuilder.get();
        }
    }

    public void runBuild() {
        try (ProjectConnection connection = createGradleConnection()) {
            // Load the project
            BuildLauncher buildLauncher = connection.newBuild();
            buildLauncher.addArguments("--quiet");
            buildLauncher.setStandardOutput(System.err);
            buildLauncher.setStandardError(System.err);

            // Add a problem listener
            ProblemListener.createAndRegister(buildLauncher);
            // Configure the task to be executed
            BuildLauncher launcher = buildLauncher.forTasks(taskPath);
            // Execute the task
            launcher.run();
        } catch (GradleConnectionException e) {
            LOGGER.error("Error connecting to Gradle.", e);
        } catch (Exception e) {
            LOGGER.error("Error executing Gradle task.", e);
        }
    }

    public static void main(String[] args) {
        String workingDir = args.length > 0 ? args[0] : System.getProperty("user.dir");
        String taskPath = args.length > 1 ? args[1] : ":sample-project:assemble";

        System.out.println("=== Importing project from " + workingDir + " ===");
        SampleIde main = new SampleIde(workingDir, taskPath);

        System.out.println("=== Running task " + taskPath + " on imported project ===");
        main.runBuild();

        System.out.println("=== Retrieving Gradle configuration with logic implemented in the 'reporters.model.builder' plugin ===");
        main.buildModel();
    }

    private static class ProblemListener implements ProgressListener {

        static void createAndRegister(LongRunningOperation operation) {
            operation.addProgressListener(new ProblemListener(), OperationType.PROBLEMS);
        }

        // tag::problems-tapi-event[]
        @Override
        public void statusChanged(ProgressEvent progressEvent) {
            if (progressEvent instanceof SingleProblemEvent)
                prettyPrint((SingleProblemEvent) progressEvent);
        }
        // end::problems-tapi-event[]

        static void prettyPrint(SingleProblemEvent problem) {
            System.out.println("Problem:");
            System.out.println(" - id: " + fqId(problem.getDefinition()));
            System.out.println(" - display name: " + problem.getDefinition().getId().getDisplayName());
            System.out.println(" - details: " + problem.getDetails().getDetails());
            System.out.println(" - severity: " + toString(problem.getDefinition().getSeverity()));
            for (Location location : problem.getLocations()) {
                if (location instanceof PluginIdLocation) {
                    System.out.println(" - plugin ID: " + ((PluginIdLocation) location).getPluginId());
                } else {
                    System.out.println(" - location: " + location);
                }
            }
            Failure exception = problem.getFailure().getFailure();
            if (exception != null) {
                System.out.println(" - exception: " + exception.getMessage());
            }
            String url = problem.getDefinition().getDocumentationLink().getUrl();
            if (url != null) {
                System.out.println(" - documentation: " + url);
            }

            List<Solution> solutions = problem.getSolutions();
            if (!solutions.isEmpty()) {
                System.out.println(" - solutions: ");
                for (Solution solution : solutions) {
                    System.out.println("   - " + solution.getSolution());
                }
            }
        }

        static String fqId(ProblemDefinition definition) {
            ProblemId id = definition.getId();
            return fqId(id.getGroup()) + ":" + id.getName();
        }

        static String fqId(ProblemGroup group) {
            ProblemGroup parent = group.getParent();
            if (parent == null) {
                return group.getName();
            } else {
                return fqId(parent) + ":" + group.getName();
            }
        }

        static String toString(Severity severity) {
            int code = severity.getSeverity();
            switch (code) {
                case 0: return "ADVICE";
                case 1: return "WARNING";
                case 2: return "ERROR";
                default: return "UNKNOWN";
            }
        }
    }
}
