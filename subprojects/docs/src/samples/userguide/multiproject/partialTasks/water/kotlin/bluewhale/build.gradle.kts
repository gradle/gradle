extra["arctic"] = true
tasks.getByName("hello") {
    doLast {
        println("- I'm the largest animal that has ever lived on this planet.")
    }
}

task("distanceToIceberg") {
    doLast {
        println("20 nautical miles")
    }
}
