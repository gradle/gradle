// tag::avoid-this[]
plugins {
    id("java-library")
    id("app-info-plugin")
}

afterEvaluate {
    the<AppInfoExtension>().appName.set("my-app") // <1>
}
// end::avoid-this[]
