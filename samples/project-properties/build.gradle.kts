// Project properties can be accessed via delegation

/**
 * The label for the answer.
 *
 * Try a different value in the command line with
 *   ./gradlew -Plabel=answer\ to\ the\ ultimate\ question\ about\ life,\ the\ universe\ and\ everything
 */
val label by project

/**
 * The answer (taken from gradle.properties)
 */
val answer by project

task("compute") {
    doLast { println("The ${label ?: "answer"} is $answer.") }
}
