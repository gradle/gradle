plugins {
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "org.example"
version = "1.0"

// tag::add-plugin-variant[]
val gradle7 = sourceSets.create("gradle7")
java {
    registerFeature(gradle7.name) {
        usingSourceSet(gradle7)
        capability(project.group.toString(), project.name, project.version.toString()) // <1>
    }
}
configurations.configureEach {
    if (isCanBeConsumed && name.startsWith(gradle7.name))  {
        attributes {
            attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, // <2>
                objects.named("7.0"))
        }
    }
}
tasks.named<Copy>(gradle7.processResourcesTaskName) { // <3>
    val copyPluginDescriptors = rootSpec.addChild()
    copyPluginDescriptors.into("META-INF/gradle-plugins")
    copyPluginDescriptors.from(tasks.pluginDescriptors)
}

dependencies {
    "gradle7CompileOnly"(gradleApi()) // <4>
}
// end::add-plugin-variant[]

// tag::consume-plugin-variant[]
configurations.configureEach {
    if (isCanBeResolved && name.startsWith(gradle7.name))  {
        attributes {
            attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
                objects.named("7.0"))
        }
    }
}
// end::consume-plugin-variant[]

gradlePlugin {
    plugins.create("greeting") {
        id = "org.example.greeting"
        implementationClass = "org.example.GreetingPlugin"
    }
}

publishing {
    repositories {
        maven { url = uri(layout.buildDirectory.dir("local-repo")) }
    }
}
