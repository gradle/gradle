tasks.register<Delete>("removeOutput") {
    delete(layout.buildDirectory.file("outputs/1.txt"))
}
