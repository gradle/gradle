tasks.register<Copy>("copyTask") {
    from("source")
    into("target")
    include("*.war")
}
