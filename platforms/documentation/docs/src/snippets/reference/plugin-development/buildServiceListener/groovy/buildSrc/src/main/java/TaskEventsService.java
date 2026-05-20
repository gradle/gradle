// tag::build-service[]
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.task.TaskFinishEvent;

public abstract class TaskEventsService implements BuildService<BuildServiceParameters.None>,
    OperationCompletionListener { // <1>

    @Override
    public void onFinish(FinishEvent finishEvent) {
        if (finishEvent instanceof TaskFinishEvent) { // <2>
            // Handle task finish event...
// end::build-service[]
            System.out.println(
                "Task finished = " + ((TaskFinishEvent) finishEvent).getDescriptor().getTaskPath());
// tag::build-service[]
        }
    }
}
// end::build-service[]
