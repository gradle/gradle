plugins {
    id("java-gradle-plugin")
    id("myproject.java-conventions")
}

repositories {
    jcenter()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

gradlePlugin {
    val greeting by plugins.creating {
        id = "com.example.plugin.greeting"
        implementationClass = "com.example.plugin.GreetingPlugin"
    }
}

val functionalTest by sourceSets.creating
gradlePlugin.testSourceSets(functionalTest)

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] + functionalTest.output
}

tasks.check.configure {
    dependsOn(functionalTestTask)
}
