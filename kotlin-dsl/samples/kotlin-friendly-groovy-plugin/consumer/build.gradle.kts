// Adapted from
//   https://docs.gradle.org/4.1/userguide/custom_plugins.html#sec:maintaining_multiple_domain_objects
//

plugins {
    id("my.documentation") version "1.0"
}

documentation {

    books {
        register("quickStart") {
            sourceFile = file("src/docs/quick-start")
        }
        register("userGuide") {

        }
        register("developerGuide") {

        }
    }
}
