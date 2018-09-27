plugins {
    checkstyle
    findbugs
    pmd
    jdepend
    jacoco
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_7
    targetCompatibility = JavaVersion.VERSION_1_7
}

dependencies {
    testCompile("junit:junit:4.12")
}

checkstyle {
    configFile = file("config/checkstyle/sun_checks.xml")
    isIgnoreFailures = true
}

findbugs {
    isIgnoreFailures = true
}

pmd {
    isIgnoreFailures = true
}

jdepend {
    isIgnoreFailures = true
}

jacoco {
    toolVersion = "0.7.9"
}

tasks {
    jacocoTestCoverageVerification {
        violationRules {
            rule { limit { minimum = BigDecimal.valueOf(0.2) } }
        }
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
    }
}

application {
    mainClassName = "samples.HelloWorld"
}

repositories {
    jcenter()
}
