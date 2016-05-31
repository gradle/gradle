import org.gradle.script.lang.kotlin.*

task("bar") {
  doLast { println("Bar!") }
}
