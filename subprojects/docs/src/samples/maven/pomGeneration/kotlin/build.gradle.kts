// tag::use-plugin[]
plugins {
    // end::use-plugin[]
    war
// tag::use-plugin[]
    maven
}
// end::use-plugin[]

group = "gradle"
version = "1.0"
base.archivesBaseName = "mywar"
buildDir = file("target")

repositories {
    flatDir {
        dirs("lib")
    }
}

configurations {
    runtime {
        exclude(group = "excludeGroup2", module = "excludeArtifact2")
    }
}

dependencies {
    compile("group1:compile:1.0") {
        exclude(group = "excludeGroup", module = "excludeArtifact")
    }
    // NOTE: explicit artifact reference makes a dependency non-transitive
    providedCompile("group2:providedCompile:1.0@jar")
    runtime("group3:runtime:1.0")
    providedRuntime("group4:providedRuntime:1.0@zip") {
        artifact {
            name = "providedRuntime-util"
            type = "war"
        }
    }
    testCompile("group5:testCompile:1.0")
    testRuntime("group6:testRuntime:1.0")
}

// Include a javadoc zip

task<Zip>("javadocZip") {
    dependsOn("javadoc")
    classifier = "javadoc"
    from(tasks.javadoc)
}

artifacts {
    add("archives", tasks["javadocZip"])
}

// Configure the release and snapshot repositories

tasks.getByName<Upload>("uploadArchives") {
    repositories.withGroovyBuilder {
        "mavenDeployer" {
            "repository"("url" to uri("pomRepo"))
            "snapshotRepository"("url" to uri("snapshotRepo"))
        }
    }
}

// Customize the contents of the pom

// tag::when-configured[]
val uploadArchives by tasks.getting(Upload::class)
val installer = tasks.install.get().repositories.withGroovyBuilder { getProperty("mavenInstaller") as MavenResolver }
val deployer = uploadArchives.repositories.withGroovyBuilder { getProperty("mavenDeployer") as MavenResolver }

listOf(installer, deployer).forEach {
    it.pom.whenConfigured {
        dependencies.firstOrNull { dep ->
            dep!!.withGroovyBuilder {
                getProperty("groupId") == "group3" && getProperty("artifactId") == "runtime"
            }
        }?.withGroovyBuilder {
            setProperty("optional", true)
        }
    }
}
// end::when-configured[]

listOf(installer, deployer).forEach {
    it.pom.version = "1.0MVN"
}
installer.pom.project {
    setProperty("groupId", "installGroup")
}
deployer.pom.groupId = "deployGroup"

// tag::new-pom[]
task("writeNewPom") {
    doLast {
        maven.pom {
            withGroovyBuilder {
                "project" {
                    setProperty("inceptionYear", "2008")
                    "licenses" {
                        "license" {
                            setProperty("name", "The Apache Software License, Version 2.0")
                            setProperty("url", "http://www.apache.org/licenses/LICENSE-2.0.txt")
                            setProperty("distribution", "repo")
                        }
                    }
                }
            }
        }.writeTo("$buildDir/newpom.xml")
    }
}

// end::new-pom[]

task("writeDeployerPom") {
    dependsOn(uploadArchives)
    doLast {
        deployer.pom.writeTo("$buildDir/deployerpom.xml")
    }
}
