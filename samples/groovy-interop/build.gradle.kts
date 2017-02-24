apply {
    from("groovy.gradle")
}

val groovySum: groovy.lang.Closure<Any?> by extra

tasks {

    "stringSum" {
        group = "My"
        description = "groovySum(\"Groovy\", \"Kotlin\")"

        doLast { println(groovySum("Groovy", "Kotlin")) }
    }

    "intSum" {
        group = "My"
        description = "groovySum(33, 11)"

        doLast { println(groovySum(33, 11)) }
    }
}
