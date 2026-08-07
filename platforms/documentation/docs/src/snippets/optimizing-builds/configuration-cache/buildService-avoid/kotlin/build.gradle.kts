// tag::avoid[]
val failedTasks = mutableListOf<String>()

gradle.taskGraph.afterTask {
    if (state.failure != null) {
        failedTasks.add(path)
    }
}

gradle.buildFinished {
    if (failedTasks.isNotEmpty()) {
        println("Failed tasks: ${failedTasks.joinToString()}")
    }
}
// end::avoid[]
