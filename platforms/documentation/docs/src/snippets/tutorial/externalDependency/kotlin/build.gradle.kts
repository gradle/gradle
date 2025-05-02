// tag::all[]
import org.apache.commons.codec.binary.Base64

// tag::declare-classpath[]
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        "classpath"(group = "commons-codec", name = "commons-codec", version = "1.2")
    }
}
// end::declare-classpath[]

tasks.register("encode") {
    doLast {
        val encodedString = Base64().encode("hello world\n".toByteArray())
        println(String(encodedString))
    }
}
// end::all[]
