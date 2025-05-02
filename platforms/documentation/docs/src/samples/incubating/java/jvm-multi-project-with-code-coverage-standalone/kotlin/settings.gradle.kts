rootProject.name = "jvm-multi-project-with-code-coverage"

// production code projects
include("application", "list", "utilities")

// reporting utility projects
include("code-coverage-report")
