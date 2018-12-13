import com.thedeanda.lorem.*

buildscript {
    dependencies {
        classpath("com.thedeanda:lorem:2.1")
    }

    repositories {
        jcenter()
    }
}

tasks.register("lorem") {
    group = "sample"
    doLast {
        println(LoremIpsum.getInstance().getParagraphs(1, 1))
    }
}
