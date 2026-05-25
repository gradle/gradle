tasks.withType<Checkstyle>().configureEach {
    minHeapSize = "200m"
    maxHeapSize = "1g"
}
