rootProject.name = "jvm-multi-project-with-test-aggregation-standalone"

// production code projects
include("application", "list", "utilities")

// reporting utility projects
include("test-results")
