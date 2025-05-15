val libs by configurations.creating

dependencies {
    libs("androidx.activity:activity-compose:1.10.1")
}

tasks.register<Copy>("copyLibs") {
    from(libs)
    into(layout.buildDirectory.dir("libs"))
}
