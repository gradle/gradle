allprojects {
    apply(plugin = "java")
    group = "org.gradle.sample"
    version = "1.0"
}

subprojects {
    apply(plugin = "war")
    repositories {
        mavenCentral()
    }
    dependencies {
        "providedCompile"("javax.servlet:servlet-api:2.5")
    }
}

tasks.register<Copy>("explodedDist") {
    into("$buildDir/explodedDist")
    subprojects {
        from(tasks.withType<War>())
    }
}
