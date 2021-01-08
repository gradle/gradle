plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("sitePlugin") {
            id = "org.myorg.site"
            implementationClass = "org.myorg.SitePlugin"
        }
    }
}
