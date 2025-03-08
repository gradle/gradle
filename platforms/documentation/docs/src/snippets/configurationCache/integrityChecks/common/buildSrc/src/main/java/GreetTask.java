import org.gradle.api.*;
import org.gradle.api.tasks.*;

// tag::task-with-broken-type[]
public abstract class GreetTask extends DefaultTask {
    User user = new User("John", 23);

    @TaskAction
    public void greet() {
        System.out.println("Hello, " + user.getName() + "!");
        System.out.println("Have a wonderful " + (user.getAge() + 1) +"th birthday!");
    }
}
// end::task-with-broken-type[]
