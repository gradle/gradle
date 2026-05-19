plugins {
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// tag::do-this[]
val functionalTest = testing.suites.register("functionalTest", JvmTestSuite::class) { // <1>
    useJUnitJupiter()
    dependencies {
        implementation("commons-io:commons-io:2.16.1")
        implementation(project())
        implementation(gradleTestKit()) // <2>
    }
}

tasks.check {
    dependsOn(functionalTest)
}

gradlePlugin {
    plugins {
        register("org.example.myplugin") {
            implementationClass = "org.example.MyPlugin"
        }
    }
    testSourceSets(functionalTest.get().sources) // <3>
}
// end::do-this[]
