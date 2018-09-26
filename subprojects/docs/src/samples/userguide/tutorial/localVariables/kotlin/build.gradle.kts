val dest = "dest"

task<Copy>("copy") {
    from("source")
    into(dest)
}
