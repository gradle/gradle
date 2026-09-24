// tag::side-effects[]
val check = tasks.register("check")
tasks.register("verificationTask") {
    // Configure verificationTask

    // Run verificationTask when someone runs check
    check.get().dependsOn(this)
}
// end::side-effects[]
