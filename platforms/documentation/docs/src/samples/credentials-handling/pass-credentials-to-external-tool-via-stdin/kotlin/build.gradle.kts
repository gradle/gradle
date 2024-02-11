val login = tasks.register<Exec>("login") {
    val loginProvider = providers.credentials(PasswordCredentials::class.java, "login")
    inputs.property("credentials", loginProvider)

    commandLine = listOf("sh", "login.sh")
    doFirst {
        val loginCredentials = loginProvider.get()
        standardInput = java.io.ByteArrayInputStream("${loginCredentials.username}\n${loginCredentials.password}".toByteArray())
    }
}

tasks.register("doAuthenticated") {
    dependsOn(login)
    doLast {
        println("Doing authenticated task")
    }
}
