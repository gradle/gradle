val jdkVariables = listOf("JDK_8", "JDK_11", "JDK_17")
val jdkLocations = jdkVariables.filter { v ->
    System.getenv(v) != null
}.associate { v ->
    v to System.getenv(v)
}
