package my

import org.gradle.api.Task
import org.gradle.model.Managed
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource


/**
 * Model rule source.
 *
 * See https://docs.gradle.org/current/userguide/software_model.html
 */
open class Rules : RuleSource() {

    @Model fun person(p: Person) {}

    @Mutate fun setSomeName(p: Person) {
        p.firstName = "John"
        p.lastName = "Smith"
    }

    // Create a rule that modifies a ModelMap<Task> and takes as input a Person
    @Mutate fun createHelloTask(tasks: ModelMap<Task>, p: Person) {
        tasks.create("hello") {
            doLast {
                println("Hello ${p.firstName} ${p.lastName}!")
            }
        }
    }
}

@Managed
interface Person {
    var firstName: String?
    var lastName: String?
}
