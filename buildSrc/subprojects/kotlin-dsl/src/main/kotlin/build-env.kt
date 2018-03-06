object BuildHost {
    val isCiServer = System.getenv().containsKey("CI")
}
