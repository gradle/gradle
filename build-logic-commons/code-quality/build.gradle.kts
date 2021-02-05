plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "gradlebuild"

dependencies {
    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:1.4.4")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.6.0")
}

tasks {

    // Temporarily capture the compileClasspath so we can investigate
    // what's going on with generateExternalPluginSpecBuilders.
    val zipCompileClasspath by registering(Zip::class) {
        archiveBaseName.set("code-quality-compile-classpath")
        from(sourceSets.main.map { it.compileClasspath })
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    generateExternalPluginSpecBuilders {
        require(this is Task)
        dependsOn(zipCompileClasspath)
    }
}
