apply<GreetingPlugin>()

tasks.withType<GreetingTask>().configureEach {
    // setting convention from build script
    guest.convention("Guest")
}
