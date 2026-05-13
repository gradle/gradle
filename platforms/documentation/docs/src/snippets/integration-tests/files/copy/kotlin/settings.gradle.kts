// tag::change-default-exclusions[]
fileSystemDefaultExcludes.set(fileSystemDefaultExcludes.get() - setOf("**/.git", "**/.git/**"))
// end::change-default-exclusions[]
rootProject.name = "copy"
