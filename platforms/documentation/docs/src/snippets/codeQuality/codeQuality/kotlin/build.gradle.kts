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
    testImplementation("junit:junit:4.13")
}

// tag::customize-checkstyle-memory[]
tasks.withType<Checkstyle>().configureEach {
    minHeapSize = "200m"
    maxHeapSize = "1g"
}
// end::customize-checkstyle-memory[]

// tag::enable-checkstyle-sarif-report[]
checkstyle {
    toolVersion = "10.3.3"
}
// end::enable-checkstyle-sarif-report[]

// tag::enable-checkstyle-sarif-report[]
// tag::customize-checkstyle-report[]
tasks.withType<Checkstyle>().configureEach {
    reports {
// end::customize-checkstyle-report[]
        sarif.required = true
// end::enable-checkstyle-sarif-report[]
// tag::customize-checkstyle-report[]
        xml.required = false
        html.required = true
        html.stylesheet = resources.text.fromFile("config/xsl/checkstyle-custom.xsl")
// tag::enable-checkstyle-sarif-report[]
    }
}
// end::enable-checkstyle-sarif-report[]
// end::customize-checkstyle-report[]

// tag::customize-pmd[]
pmd {
    isConsoleOutput = true
    toolVersion = "6.21.0"
    rulesMinimumPriority = 5
    ruleSets = listOf("category/java/errorprone.xml", "category/java/bestpractices.xml")
}
// end::customize-pmd[]

// tag::pmd-threads[]
pmd {
    threads = 4
}
// end::pmd-threads[]
