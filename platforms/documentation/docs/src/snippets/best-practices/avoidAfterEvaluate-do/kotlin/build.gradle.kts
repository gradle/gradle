// tag::do-this[]
plugins {
    id("java-library")
    id("app-info-plugin")
}

appInfo {
    appName.set("my-app") // <1>
}
// end::do-this[]
