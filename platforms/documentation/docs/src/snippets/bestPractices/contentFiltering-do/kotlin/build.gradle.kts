import org.gradle.api.attributes.java.TargetJvmEnvironment

val libs by configurations.creating {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}

dependencies {
    libs("androidx.activity:activity-compose:1.10.1")
    libs("com.google.gms:google-services:4.4.3")
}

tasks.register<Copy>("copyLibs") {
    from(libs)
    into(layout.buildDirectory.dir("libs"))
}
