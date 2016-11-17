import org.gradle.api.JavaVersion.VERSION_1_7

apply<ApplicationPlugin>()

configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorld"
}

configure<JavaPluginConvention> {
    sourceCompatibility = VERSION_1_7
    targetCompatibility = VERSION_1_7
}

repositories {
    jcenter()
}

dependencies {
    testCompile("junit:junit:4.12")
}
