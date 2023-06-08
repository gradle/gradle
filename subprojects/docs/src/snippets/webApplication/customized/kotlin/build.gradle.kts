// tag::use-plugin[]
plugins {
    war
}
// end::use-plugin[]

group = "gradle"
version = "1.0"

// tag::customization[]
repositories {
    mavenCentral()
}

dependencies {
    providedCompile("javax.servlet:servlet-api:2.5")
}

tasks.war {
    webAppDirectory.set(file("src/main/webapp"))
    from("src/rootContent") // adds a file-set to the root of the archive
    webInf { from("src/additionalWebInf") } // adds a file-set to the WEB-INF dir.
    webXml = file("src/someWeb.xml") // copies a file to WEB-INF/web.xml
}
// end::customization[]
