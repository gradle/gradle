package configurations


private val linuxJava8Oracle = "%linux.java8.oracle.64bit%"
private val linuxJava11Openjdk = "%linux.java11.openjdk.64bit%"

val buildJavaHome = linuxJava11Openjdk
val coordinatorPerformanceTestJavaHome = linuxJava8Oracle
val individualPerformanceTestJavaHome = linuxJava8Oracle
val smokeTestJavaHome = linuxJava8Oracle
val distributionTestJavaHome = linuxJava11Openjdk

