
withHelloTask()

tasks.register("printProfile") {
    description = "Uses the extension property and the extension functions defined in buildSrc. Use with -Pprofile=prod."
    group = "sample"

    doLast {
        println("The current profile is $profile")
        logProfile()
    }
}
