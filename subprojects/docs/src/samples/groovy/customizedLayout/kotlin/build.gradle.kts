plugins{
    groovy
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy:2.4.15")
    testImplementation("junit:junit:4.12")
}

// tag::custom-source-locations[]
sourceSets {
    getByName("main") {
        withConvention(GroovySourceSet::class) {
            groovy {
                setSrcDirs(listOf("src/groovy"))
            }
        }
    }

    getByName("test") {
        withConvention(GroovySourceSet::class) {
            groovy {
                setSrcDirs(listOf("test/groovy"))
            }
        }
    }
}
// end::custom-source-locations[]
