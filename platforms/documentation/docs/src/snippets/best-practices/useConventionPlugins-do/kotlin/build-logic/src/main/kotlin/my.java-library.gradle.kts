// tag::do-this[]
plugins { // <2>
    id("my.base-java-library")
    id("my.java-use-junit5")
}
// end::do-this[]

val conventionsApplied = tasks.register("myJavaLibraryApplied")
tasks.compileJava.configure { dependsOn(conventionsApplied) }
