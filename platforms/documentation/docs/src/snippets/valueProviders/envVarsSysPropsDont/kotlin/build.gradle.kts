val jdkLocations = System.getenv().filterKeys {
    it.startsWith("JDK_")
}
