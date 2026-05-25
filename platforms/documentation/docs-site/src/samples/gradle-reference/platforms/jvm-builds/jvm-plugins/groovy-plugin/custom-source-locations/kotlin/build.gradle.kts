sourceSets {
    main {
        groovy {
            setSrcDirs(listOf("src/groovy"))
        }
    }

    test {
        groovy {
            setSrcDirs(listOf("test/groovy"))
        }
    }
}
