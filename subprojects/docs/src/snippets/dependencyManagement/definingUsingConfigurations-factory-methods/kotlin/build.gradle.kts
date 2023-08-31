// tag::declare-configurations[]
configurations {
    dependencyScope("implementation")
    dependencyScope("runtimeOnly")
    resolvable("compileClasspath") {
        extendsFrom(configurations["implementation"])
    }
    resolvable("runtimeClasspath") {
        extendsFrom(configurations["implementation"])
        extendsFrom(configurations["runtimeOnly"])
    }
    consumable("apiElements") {
        extendsFrom(configurations["implementation"])
    }
    consumable("runtimeElements") {
        extendsFrom(configurations["implementation"])
        extendsFrom(configurations["runtimeOnly"])
    }
}
// end::declare-configurations[]
