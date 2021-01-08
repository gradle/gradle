val dest = "dest"

tasks.register<Copy>("copy") {
    from("source")
    into(dest)
}
