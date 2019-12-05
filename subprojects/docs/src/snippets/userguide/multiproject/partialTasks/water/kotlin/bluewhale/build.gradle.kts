extra["arctic"] = true
tasks.named("hello") {
    doLast {
        println("- I'm the largest animal that has ever lived on this planet.")
    }
}

tasks.register("distanceToIceberg") {
    doLast {
        println("20 nautical miles")
    }
}
