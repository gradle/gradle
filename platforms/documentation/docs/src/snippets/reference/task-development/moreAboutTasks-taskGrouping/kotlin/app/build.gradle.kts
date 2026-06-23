plugins {
    id("application")
}

// tag::task-grouping[]
val myBuildGroup = "my app build"               // Create a group name

tasks.register<TaskReportTask>("tasksAll") {    // Register the tasksAll task
group = myBuildGroup
description = "Show additional tasks."
setShowDetail(true)
}

tasks.named<TaskReportTask>("tasks") {          // Move all existing tasks to the group
displayGroup = myBuildGroup
}
// end::task-grouping[]
