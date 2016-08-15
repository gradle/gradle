apply<ApplicationPlugin>()

configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorld"
}

configure<JavaPluginConvention> {
    setSourceCompatibility(1.7)
    setTargetCompatibility(1.7)
}

repositories {
    jcenter()
}

dependencies {
    testCompile("junit:junit:4.12")
}
