plugins {
    `java-library`
}

configurations {
    val myCodeCompileClasspath: Configuration by creating
}

sourceSets {
    val myCode: SourceSet by creating
}
