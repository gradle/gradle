# Groovy Closures in Gradle Script Kotlin

Most third party plugins are written in groovy with the expectation that closures will 
be passed as arguments. In order to provide a way to construct closures while preserving kotlin's
strong typing two helper methods exist: 
 - `closureOf<...> {...}` 
 - `delegateClosureOf<...> {...}`.

Both methods are useful in different circumstances and depend upon the method you are passing the 
closure into. To understand the difference between the two types of closures [this document](http://groovy-lang.org/closures.html)
may be helpful.

Some plugins expect only closures:

```kotlin
val tests = mapOf(
        "API" to "src/test/resources/api/ApiTestSuite.xml",
        "System" to "src/test/resources/system/SystemTestSuite.xml"
)

val testTasks = tests.map {
    val (name, path) = it
    task<Test>("test$name") {
        val testNGOptions = closureOf<TestNGOptions> {
            suites(path)
        }
        useTestNG(testNGOptions)
    }
}
```

In other cases, like in the [Gretty Plugin](https://github.com/akhikhl/gretty) when configuring farms,
the plugin expects a delegate closure:
```kotlin
configure<FarmsExtension> {
    farm("OldCoreWar", delegateClosureOf<FarmExtension> {
        // Config for the war here
    }
}
```

There sometimes isn't a good way to tell, from looking at the source code, which version to use.
Usually, if you get a `NullPointerException` with the `closureOf`, using `delegateClosureOf`
will resolve the problem.
