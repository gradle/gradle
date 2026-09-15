package example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GreetingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("hello", task ->
            task.doLast(t -> System.out.println("Hello from GreetingPlugin"))
        );
    }
}
