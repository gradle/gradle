// tag::custom-configuration[]
val jasper by configurations.creating

repositories {
    mavenCentral()
}

dependencies {
    jasper("org.apache.tomcat.embed:tomcat-embed-jasper:9.0.2")
}

tasks.register("preCompileJsps") {
    doLast {
        ant.withGroovyBuilder {
            "taskdef"("classname" to "org.apache.jasper.JspC",
                      "name" to "jasper",
                      "classpath" to jasper.asPath)
            "jasper"("validateXml" to false,
                     "uriroot" to file("src/main/webapp"),
                     "outputDir" to file("$buildDir/compiled-jsps"))
        }
    }
}
// end::custom-configuration[]
