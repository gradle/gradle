// tag::maven-ivy-repository-no-auth[]
repositories {
    maven {
        url = uri("http://repo.mycompany.com/maven2")
    }

    ivy {
        url = uri("http://repo.mycompany.com/repo")
    }
}
// end::maven-ivy-repository-no-auth[]

// tag::maven-ivy-repository-auth[]
repositories {
    maven {
        url = uri("sftp://repo.mycompany.com:22/maven2")
        credentials {
            username = "user"
            password = "password"
        }
    }

    ivy {
        url = uri("sftp://repo.mycompany.com:22/repo")
        credentials {
            username = "user"
            password = "password"
        }
    }
}
// end::maven-ivy-repository-auth[]

// tag::maven-ivy-s3-repository[]
repositories {
    maven {
        url = uri("s3://myCompanyBucket/maven2")
        credentials(AwsCredentials::class) {
            accessKey = "someKey"
            secretKey = "someSecret"
            // optional
            sessionToken = "someSTSToken"
        }
    }

    ivy {
        url = uri("s3://myCompanyBucket/ivyrepo")
        credentials(AwsCredentials::class) {
            accessKey = "someKey"
            secretKey = "someSecret"
            // optional
            sessionToken = "someSTSToken"
        }
    }
}
// end::maven-ivy-s3-repository[]

// tag::maven-ivy-s3-repository-with-iam[]
repositories {
    maven {
        url = uri("s3://myCompanyBucket/maven2")
        authentication {
            create<AwsImAuthentication>("awsIm") // load from EC2 role or env var
        }
    }

    ivy {
        url = uri("s3://myCompanyBucket/ivyrepo")
        authentication {
            create<AwsImAuthentication>("awsIm")
        }
    }
}
// end::maven-ivy-s3-repository-with-iam[]

// tag::maven-ivy-gcs-repository[]
repositories {
    maven {
        url = uri("gcs://myCompanyBucket/maven2")
    }

    ivy {
        url = uri("gcs://myCompanyBucket/ivyrepo")
    }
}
// end::maven-ivy-gcs-repository[]

// tag::maven-central[]
repositories {
    mavenCentral()
}
// end::maven-central[]

// tag::maven-google[]
repositories {
    google()
}
// end::maven-google[]

// tag::maven-local[]
repositories {
    mavenLocal()
}
// end::maven-local[]

// tag::maven-like-repo[]
repositories {
    maven {
        url = uri("http://repo.mycompany.com/maven2")
    }
}
// end::maven-like-repo[]

// tag::maven-like-repo-with-jar-repo[]
repositories {
    maven {
        // Look for POMs and artifacts, such as JARs, here
        url = uri("http://repo2.mycompany.com/maven2")
        // Look for artifacts here if not found at the above location
        artifactUrls("http://repo.mycompany.com/jars")
        artifactUrls("http://repo.mycompany.com/jars2")
    }
}
// end::maven-like-repo-with-jar-repo[]

// tag::authenticated-maven-repo[]
repositories {
    maven {
        url = uri("http://repo.mycompany.com/maven2")
        credentials {
            username = "user"
            password = "password"
        }
    }
}
// end::authenticated-maven-repo[]

// tag::header-authenticated-maven-repo[]
repositories {
    maven {
        url = uri("http://repo.mycompany.com/maven2")
        credentials(HttpHeaderCredentials::class) {
            name = "Private-Token"
            value = "TOKEN"
        }
        authentication {
            create<HttpHeaderAuthentication>("header")
        }
    }
}
// end::header-authenticated-maven-repo[]

// tag::flat-dir[]
// tag::flat-dir-multi[]
repositories {
    flatDir {
        dirs("lib")
    }
// end::flat-dir[]
    flatDir {
        dirs("lib1", "lib2")
    }
// tag::flat-dir[]
}
// end::flat-dir[]
// end::flat-dir-multi[]

// tag::ivy-repo[]
repositories {
    ivy {
        url = uri("http://repo.mycompany.com/repo")
    }
}
// end::ivy-repo[]

// tag::local-ivy-repo[]
repositories {
    ivy {
        // URL can refer to a local directory
        url = uri("../local-repo")
    }
}
// end::local-ivy-repo[]

// tag::ivy-repo-with-maven-layout[]
repositories {
    ivy {
        url = uri("http://repo.mycompany.com/repo")
        layout("maven")
    }
}
// end::ivy-repo-with-maven-layout[]

// the casts in the three following sections should be unnecessary once
// https://github.com/gradle/gradle/issues/6529 is fixed.

// tag::ivy-repo-with-pattern-layout[]
repositories {
    ivy {
        url = uri("http://repo.mycompany.com/repo")
        patternLayout {
            artifact("[module]/[revision]/[type]/[artifact].[ext]")
        }
    }
}
// end::ivy-repo-with-pattern-layout[]

// tag::ivy-repo-with-m2compatible-layout[]
repositories {
    ivy {
        url = uri("http://repo.mycompany.com/repo")
        patternLayout {
            artifact("[organisation]/[module]/[revision]/[artifact]-[revision].[ext]")
            setM2compatible(true)
        }
    }
}
// end::ivy-repo-with-m2compatible-layout[]

// tag::ivy-repo-with-custom-pattern[]
repositories {
    ivy {
        url = uri("http://repo.mycompany.com/repo")
        patternLayout {
            artifact("3rd-party-artifacts/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]")
            artifact("company-artifacts/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]")
            ivy("ivy-files/[organisation]/[module]/[revision]/ivy.xml")
        }
    }
}
// end::ivy-repo-with-custom-pattern[]

// tag::maven-repo-with-metadata-sources[]
repositories {
    maven {
        url = uri("http://repo.mycompany.com/repo")
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}
// end::maven-repo-with-metadata-sources[]

// tag::maven-repo-with-ignore-gradle-metadata-redirection[]
repositories {
    maven {
        url = uri("http://repo.mycompany.com/repo")
        metadataSources {
            mavenPom()
            artifact()
            ignoreGradleMetadataRedirection()
        }
    }
}
// end::maven-repo-with-ignore-gradle-metadata-redirection[]

// tag::authenticated-ivy-repo[]
repositories {
    ivy {
        url = uri("http://repo.mycompany.com")
        credentials {
            username = "user"
            password = "password"
        }
    }
}
// end::authenticated-ivy-repo[]

tasks.register("lookup") {
    val repoNames = repositories.map { it.name }
    doLast {
        repoNames.forEach { require(it != null) }
        require(repoNames[0] != null)
    }
}

// tag::digest-authentication[]
repositories {
    maven {
        url = uri("https://repo.mycompany.com/maven2")
        credentials {
            username = "user"
            password = "password"
        }
        authentication {
            create<DigestAuthentication>("digest")
        }
    }
}
// end::digest-authentication[]

// tag::preemptive-authentication[]
repositories {
    maven {
        url = uri("https://repo.mycompany.com/maven2")
        credentials {
            username = "user"
            password = "password"
        }
        authentication {
            create<BasicAuthentication>("basic")
        }
    }
}
// end::preemptive-authentication[]
