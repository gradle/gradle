
plugins {
    groovy // <1>

    application // <2>
}

repositories {
    jcenter() // <3>
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.5.12") // <4>

    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5") // <5>
}

application {
    mainClass.set("demo.App") // <6>
}
