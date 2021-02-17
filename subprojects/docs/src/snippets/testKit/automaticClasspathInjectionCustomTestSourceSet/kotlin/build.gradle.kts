// tag::custom-test-source-set[]
plugins {
    groovy
    `java-gradle-plugin`
}

sourceSets {
    create("functionalTest") {
        withConvention(GroovySourceSet::class) {
            groovy {
                srcDir(file("src/functionalTest/groovy"))
            }
        }
        resources {
            srcDir(file("src/functionalTest/resources"))
        }
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

tasks.register<Test>("functionalTest") {
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
}

tasks.check { dependsOn(tasks["functionalTest"]) }

gradlePlugin {
    testSourceSets(sourceSets["functionalTest"])
}

dependencies {
    "functionalTestImplementation"("org.spockframework:spock-core:2.0-M4-groovy-3.0") {
        exclude(module = "groovy-all")
    }
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter-api")
}
// end::custom-test-source-set[]

repositories {
    mavenCentral()
}
