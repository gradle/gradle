tasks {
    test {
        mustRunAfter(checkstyleMain, checkstyleTest)
    }
}
