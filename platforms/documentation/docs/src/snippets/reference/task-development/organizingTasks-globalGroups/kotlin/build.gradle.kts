// tag::global-groups[]
val globalBuildGroup = "My global build"
val ciBuildGroup = "My CI build"

tasks.named<TaskReportTask>("tasks") {
    displayGroups = listOf<String>(globalBuildGroup, ciBuildGroup)
}
// end::global-groups[]
