dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation("com.vladsch.flexmark:flexmark-all:0.34.48")
    implementation("com.uwyn:jhighlight:1.0") {
        exclude(module = "servlet-api")
    }
}
