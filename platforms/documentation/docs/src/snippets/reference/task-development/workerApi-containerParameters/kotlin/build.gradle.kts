// tag::register-task[]
tasks.register<PrintServersTask>("printServers") {
    servers.register("staging") { url = "https://staging.example.com" }
    servers.register("production") { url = "https://example.com" }
}
// end::register-task[]
