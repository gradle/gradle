subprojects {
    apply(plugin = "java")
    group = "org.gradle.sample"
    version = "1.0"
}

project(":api") {
    configurations {
        create("spi")
    }
    dependencies {
        "implementation"(project(":shared"))
    }
    tasks.register<Jar>("spiJar") {
        archiveBaseName.set("api-spi")
        from(project.the<SourceSetContainer>()["main"].output)
        include("org/gradle/sample/api/**")
    }
    artifacts {
        add("spi", tasks["spiJar"])
    }
}

project(":services:personService") {
    dependencies {
        "implementation"(project(":shared"))
        "implementation"(project(path = ":api", configuration = "spi"))
        "testImplementation"("junit:junit:4.12")
        "testImplementation"(project(":api"))
    }
}
