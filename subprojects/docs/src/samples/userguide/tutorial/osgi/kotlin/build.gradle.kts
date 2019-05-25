plugins {
    osgi
    java
}

// tag::configure-jar[]
tasks.withType<Jar>().configureEach {
    manifest {
        // the manifest of the default jar is of type OsgiManifest
        (manifest as? OsgiManifest)?.apply {
            name = "overwrittenSpecialOsgiName"
            instruction("Private-Package",
                "org.mycomp.package1",
                "org.mycomp.package2")
            instruction("Bundle-Vendor", "MyCompany")
            instruction("Bundle-Description", "Platform2: Metrics 2 Measures Framework")
            instruction("Bundle-DocURL", "http://www.mycompany.com")
        }
    }
}
tasks.register<Jar>("fooJar") {
    manifest = osgi.osgiManifest {
        instruction("Bundle-Vendor", "MyCompany")
    }
}
// end::configure-jar[]
