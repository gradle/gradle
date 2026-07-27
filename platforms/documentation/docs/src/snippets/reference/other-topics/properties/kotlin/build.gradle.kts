// tag::set-property[]
ant.setProperty("buildDir", buildDir)
ant.properties.set("buildDir", buildDir)
ant.properties["buildDir"] = buildDir
ant.withGroovyBuilder {
    "property"("name" to "buildDir", "location" to "buildDir")
}
// end::set-property[]

ant.importBuild("build.xml")

// tag::get-property[]
println(ant.getProperty("antProp"))
println(ant.properties.get("antProp"))
println(ant.properties["antProp"])
// end::get-property[]

// tag::set-reference[]
ant.withGroovyBuilder { "path"("id" to "classpath", "location" to "libs") }
ant.references.set("classpath", ant.withGroovyBuilder { "path"("location" to "libs") })
ant.references["classpath"] = ant.withGroovyBuilder { "path"("location" to "libs") }
// end::set-reference[]

// tag::get-reference[]
println(ant.references.get("antPath"))
println(ant.references["antPath"])
// end::get-reference[]
