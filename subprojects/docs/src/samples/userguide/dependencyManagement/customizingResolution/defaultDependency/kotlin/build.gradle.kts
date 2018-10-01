// tag::configuration-default-dependencies[]
configurations {
    create("pluginTool") {
        defaultDependencies {
            add(project.dependencies.create("org.gradle:my-util:1.0"))
        }
    }
}
// end::configuration-default-dependencies[]
