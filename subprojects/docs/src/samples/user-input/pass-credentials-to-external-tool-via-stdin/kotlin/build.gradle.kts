val login = tasks.register<Exec>("login") {
    val username: String by project
    val password: String by project
    standardInput = java.io.ByteArrayInputStream("$username\n$password".toByteArray())
    commandLine = listOf("sh", "login.sh")
}

tasks.register("doAuthenticated") {
    dependsOn(login)
    doLast {
        println("doAuthenticated")
    }
}
