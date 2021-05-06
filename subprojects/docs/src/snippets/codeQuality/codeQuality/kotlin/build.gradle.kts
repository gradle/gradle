// tag::use-checkstyle-plugin[]
// tag::use-codenarc-plugin[]
// tag::use-pmd-plugin[]
plugins {
// end::use-checkstyle-plugin[]
// end::use-codenarc-plugin[]
// end::use-pmd-plugin[]
    groovy
// tag::use-checkstyle-plugin[]
    checkstyle
// end::use-checkstyle-plugin[]
// tag::use-codenarc-plugin[]
    codenarc
// end::use-codenarc-plugin[]
// tag::use-pmd-plugin[]
    pmd
// tag::use-checkstyle-plugin[]
// tag::use-codenarc-plugin[]
}
// end::use-checkstyle-plugin[]
// end::use-codenarc-plugin[]
// end::use-pmd-plugin[]

codenarc {
    toolVersion = "2.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(localGroovy())
    testImplementation("junit:junit:4.13")
}

// tag::customize-checkstyle-report[]
tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
        html.stylesheet = resources.text.fromFile("config/xsl/checkstyle-custom.xsl")
    }
}
// end::customize-checkstyle-report[]

// tag::customize-pmd[]
pmd {
    isConsoleOutput = true
    toolVersion = "6.21.0"
    rulesMinimumPriority.set(5)
    ruleSets = listOf("category/java/errorprone.xml", "category/java/bestpractices.xml")
}
// end::customize-pmd[]
