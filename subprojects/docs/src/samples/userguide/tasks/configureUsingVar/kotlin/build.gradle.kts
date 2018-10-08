task<Copy>("myCopy")

// tag::configure[]
val myCopy = tasks.getByName<Copy>("myCopy")
myCopy.from("resources")
myCopy.into("target")
myCopy.include("**/*.txt", "**/*.xml", "**/*.properties")
// end::configure[]
