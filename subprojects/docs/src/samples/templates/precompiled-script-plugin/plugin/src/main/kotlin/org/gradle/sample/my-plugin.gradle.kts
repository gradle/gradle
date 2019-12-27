// This script is automatically exposed to downstream consumers
// as the `org.gradle.sample.my-plugin` org.gradle.api.Project plugin
package org.gradle.sample

tasks {
    register("greet") {
        group = "sample"
        doLast {
            println("Hello, World!")
        }
    }
}
