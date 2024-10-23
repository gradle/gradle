// tag::code[]
import org.apache.commons.codec.binary.Base64

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("commons-codec:commons-codec:1.15") // This will be available only for the build script
    }
}

tasks.register("encode") {
    doLast {
        val encodedString: ByteArray = Base64().encode("hello world from root build file\n".toByteArray())
        println(String(encodedString))
    }
}
// end::code[]

// tag::apply[]
plugins {
    id("my-create-file-plugin")  // Apply the convention plugin
    id("com.example.my-binary-plugin") // Apply the binary plugin
    `kotlin-dsl`
}
// end::apply[]

// tag::plugin-1[]
abstract class SamplePlugin1 : Plugin<Project> { // <1>
    override fun apply(project: Project) {  // <2>
        project.tasks.register("helloTaskInRootBuildFileSamplePlugin1") {
            println("Hello world from the root build file!")
        }
    }
}
// end::plugin-1[]
apply<SamplePlugin1>()

// tag::plugin-2[]
abstract class SamplePlugin2 : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("createFileTaskInRootBuildFileSamplePlugin2") {
            val fileText = "HELLO FROM MY SCRIPT PLUGIN"
            val myFile = File("myfile.txt")
            myFile.createNewFile()
            myFile.writeText(fileText)
        }
    }
}

apply<SamplePlugin2>()
// end::plugin-2[]
