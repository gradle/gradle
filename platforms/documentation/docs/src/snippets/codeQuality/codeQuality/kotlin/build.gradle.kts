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

// tag::specify-groovy-version[]
dependencies {
// end::specify-groovy-version[]
    implementation(localGroovy())
    testImplementation("junit:junit:4.13")
// tag::specify-groovy-version[]
    "codenarc"("org.codehaus.groovy:groovy-all:3.0.3")
    "codenarc"("org.codenarc:CodeNarc:1.6.1")
}
// end::specify-groovy-version[]

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
    toolVersion = "7.0.0"
    rulesMinimumPriority = 5
    ruleSets = listOf("category/java/errorprone.xml", "category/java/bestpractices.xml")
}
// end::customize-pmd[]

// tag::pmd-threads[]
pmd {
    threads = 4
}
// end::pmd-threads[]
