plugins {
    id("org.myorg.site")
}

site {
    outputDir.set(layout.buildDirectory.file("mysite"))
    websiteUrl.set("https://gradle.org")
    vcsUrl.set("https://github.com/gradle/gradle-site-plugin")
}
