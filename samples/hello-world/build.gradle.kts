apply<ApplicationPlugin>()

configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorld"
}

repositories {
    jcenter()
}

dependencies {
    "testCompile"("junit:junit:4.12")
}
