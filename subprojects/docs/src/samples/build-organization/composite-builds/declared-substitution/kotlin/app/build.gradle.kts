plugins {
    java
    application
}

application {
    mainClass.set("org.sample.myapp.Main")
}

dependencies {
    implementation("org.sample:number-utils:1.0")
}
