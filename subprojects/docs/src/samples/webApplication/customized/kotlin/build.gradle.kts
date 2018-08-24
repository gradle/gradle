// tag::use-plugin[]
plugins {
    war
    // end::use-plugin[]
    id("org.gretty") version "2.1.0"
    // tag::use-plugin[]
}
// end::use-plugin[]

group = "gradle"
version = "1.0"

// tag::customization[]
val moreLibs by configurations.creating

repositories {
    flatDir { dir("lib") }
    jcenter()
}

dependencies {
    compile(module(":compile:1.0") {
        dependency(":compile-transitive-1.0@jar")
        dependency( ":providedCompile-transitive:1.0@jar")
    })
    providedCompile("javax.servlet:servlet-api:2.5")
    providedCompile(module(":providedCompile:1.0") {
        dependency(":providedCompile-transitive:1.0@jar")
    })
    runtime(":runtime:1.0")
    providedRuntime(":providedRuntime:1.0@jar")
    testCompile("junit:junit:4.12")
    moreLibs(":otherLib:1.0")
}

tasks.getting(War::class) {
    from("src/rootContent") // adds a file-set to the root of the archive
    webInf { from("src/additionalWebInf") } // adds a file-set to the WEB-INF dir.
    classpath(fileTree("additionalLibs")) // adds a file-set to the WEB-INF/lib dir.
    classpath(moreLibs) // adds a configuration to the WEB-INF/lib dir.
    webXml = file("src/someWeb.xml") // copies a file to WEB-INF/web.xml
}
// end::customization[]
gretty {
    httpPort = 8080
}
