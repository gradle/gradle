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

repositories {
    mavenCentral()
}

dependencies {
    implementation(localGroovy())
    testImplementation("junit:junit:4.12")
}

// tag::customize-checkstyle-report[]
tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.isEnabled = false
        html.isEnabled = true
        html.stylesheet = resources.text.fromFile("config/xsl/checkstyle-custom.xsl")
    }
}
// end::customize-checkstyle-report[]
