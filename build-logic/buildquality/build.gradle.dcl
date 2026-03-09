kotlinDslPlugin {
    description = "Provides plugins to configure quality checks (incubating report, CodeNarc, et al)"

    dependencies {
        implementation("gradlebuild:basics")

        implementation(project(":cleanup"))
        implementation(project(":documentation"))
        implementation(project(":integration-testing"))
        implementation(project(":jvm"))
        implementation(project(":performance-testing"))
        implementation(project(":profiling"))
        implementation(project(":binary-compatibility"))
        implementation(project(":dependency-modules"))

        // FIXME: cannot use catalog() + action-taking dependency notation
        implementation("org.codenarc:CodeNarc:3.6.0-groovy-4.0") {
            exclude(mapOf("group" to "org.apache.groovy"))
            exclude(mapOf("group" to "org.codehaus.groovy"))
        }
        implementation("com.github.javaparser:javaparser-symbol-solver-core:3.18.0") {
            exclude(mapOf("group" to "com.google.guava"))
        }
        implementation(catalog("buildLibs.kgp"))
        compileOnly(catalog("buildLibs.kotlinCompilerEmbeddable")) {
            because("Required by IncubatingApiReportTask")
        }
        implementation(catalog("buildLibs.develocityPlugin")) {
            because("Arch-test plugin configures the PTS extension")
        }

        testImplementation(catalog("testLibs.junit5JupiterEngine"))
        testImplementation(catalog("buildLibs.commonsLang3"))
    }
}
