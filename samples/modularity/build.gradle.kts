import org.gradle.script.lang.kotlin.*

apply {
    from("foo.gradle.kts")
    from("bar.gradle.kts")
}
