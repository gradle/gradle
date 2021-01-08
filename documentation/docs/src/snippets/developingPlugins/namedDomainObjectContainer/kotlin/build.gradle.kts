plugins {
    id("org.myorg.server-env")
}

environments {
    create("dev") {
        url.set("http://localhost:8080")
    }

    create("staging") {
        url.set("http://staging.enterprise.com")
    }

    create("production") {
        url.set("http://prod.enterprise.com")
    }
}
