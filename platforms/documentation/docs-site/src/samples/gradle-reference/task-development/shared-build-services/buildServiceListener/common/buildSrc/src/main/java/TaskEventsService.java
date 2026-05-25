import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.task.TaskFinishEvent;

public abstract class TaskEventsService implements BuildService<BuildServiceParameters.None>,
    OperationCompletionListener { // <1> Implement the `OperationCompletionListener` interface and the `BuildService` interface.

    @Override
    public void onFinish(FinishEvent finishEvent) {
        if (finishEvent instanceof TaskFinishEvent) { // <2> Check if the finish event is a `TaskFinishEvent`.
            // Handle task finish event...
        }
    }
}
