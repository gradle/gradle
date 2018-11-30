package configurations_g4


private val linuxJava8Oracle = "%linux.java8.oracle.64bit%"
private val linuxJava9Oracle = "%linux.java9.oracle.64bit%"

val buildJavaHome = linuxJava9Oracle
val coordinatorPerformanceTestJavaHome = linuxJava8Oracle
val individualPerformanceTestJavaHome = linuxJava8Oracle
val smokeTestJavaHome = linuxJava8Oracle
val distributionTestJavaHome = linuxJava9Oracle

