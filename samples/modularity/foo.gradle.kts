import org.gradle.script.lang.kotlin.*

task("foo") {
  doLast { println("Foo!") }
}
