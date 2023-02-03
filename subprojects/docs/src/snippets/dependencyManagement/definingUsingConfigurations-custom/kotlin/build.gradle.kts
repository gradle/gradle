// tag::custom-configuration[]
val jasper by configurations.creating

repositories {
    mavenCentral()
}

dependencies {
    jasper("org.apache.tomcat.embed:tomcat-embed-jasper:9.0.2")
}

tasks.register("preCompileJsps") {
    val jasperClasspath = jasper.asPath
    val projectLayout = layout
    doLast {
        ant.withGroovyBuilder {
            "taskdef"("classname" to "org.apache.jasper.JspC",
                      "name" to "jasper",
                      "classpath" to jasperClasspath)
            "jasper"("validateXml" to false,
                     "uriroot" to projectLayout.projectDirectory.file("src/main/webapp").asFile,
                     "outputDir" to projectLayout.buildDirectory.file("compiled-jsps").get().asFile)
        }
    }
}
// end::custom-configuration[]
