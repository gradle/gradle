plugins {
    // Apply the `my-plugin` defined in buildSrc/src/main/kotlin/my-plugin.gradle.kts
    `my-plugin`
}

my {
    flag.set(true)
}

withHelloTask()

tasks.register("printProfile") {
    description = "Uses the extension property and the extension functions defined in buildSrc. Use with -Pprofile=prod."
    group = "sample"

    doLast {
        println("The current profile is $profile")
        logProfile()
    }
}
