groovyKotlinDslPlugin {
    description = "Provides a plugin for configuring japicmp-gradle-plugin to detect binary incompatible changes"

    dependencies {
        api(catalog("buildLibs.japiCmpPlugin"))

        implementation(project(":dependency-modules"))
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")

        implementation(catalog("buildLibs.javaParserCore"))
        implementation(catalog("buildLibs.gson"))
        implementation(catalog("buildLibs.guava"))
        implementation(catalog("buildLibs.javaAssist"))
        implementation(catalog("buildLibs.kotlinMetadata"))
        implementation(catalog("buildLibs.jspecify"))
        implementation(catalog("libs.asm"))
        compileOnly(catalog("buildLibs.kotlinCompilerEmbeddable"))

        testImplementation(catalog("buildLibs.jsoup"))
        testImplementation(catalog("testLibs.junit5JupiterEngine"))
    }
}
