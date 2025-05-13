// tag::do-this[]
// This is the build.gradle file for the util-commons module

plugins { // <4>
    `java-library`
}

dependencies { // <5>
    api(project(":util"))
    implementation("commons-lang:commons-lang:2.6")
}
// end::do-this[]
