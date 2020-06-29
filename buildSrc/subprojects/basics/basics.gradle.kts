dependencies {
    implementation(kotlin("compiler-embeddable") as String) {
        because("Required by KotlinSourceParser")
    }
    implementation(kotlin("gradle-plugin") as String) {
        because("For manually defined KotlinSourceSet accessor - sourceSets.main.get().kotlin")
    }
}
