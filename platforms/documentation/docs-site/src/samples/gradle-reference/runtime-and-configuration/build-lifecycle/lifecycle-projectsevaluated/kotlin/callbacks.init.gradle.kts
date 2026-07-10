// 6. projectsEvaluated: all projects are fully configured, safe for cross-project checks
// to be called when all projects for the build have been evaluated.
gradle.projectsEvaluated {
    println("[projectsEvaluated] All projects evaluated")

    // Example: globally configure the java plugin
    allprojects {
        extensions.findByType<JavaPluginExtension>()?.let { javaExtension ->
            if (javaExtension.toolchain.languageVersion.isPresent) {
                println("[projectsEvaluated] ${path} uses Java plugin with toolchain ${javaExtension.toolchain.displayName}")
            } else {
                println("[projectsEvaluated] WARNING: ${path} uses Java plugin but no toolchain is configured, setting Java 17")
                javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }
}
