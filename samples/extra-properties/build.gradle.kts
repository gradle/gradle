import org.gradle.script.lang.kotlin.*
import org.gradle.api.tasks.wrapper.*

val myTask = task("myTask") {

    extra["foo"] = 42

    doLast {
        println("Extra property value: ${extra["foo"]}")
    }
}

afterEvaluate {
    println("myTask.foo = ${myTask.extra["foo"]}")
}

defaultTasks(myTask.name)

tasks.withType<Wrapper> {
    distributionUrl = "https://repo.gradle.org/gradle/demo/demo.zip"
}
