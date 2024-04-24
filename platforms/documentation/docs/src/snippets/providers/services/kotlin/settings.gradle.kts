import org.gradle.initialization.layout.BuildLayout

rootProject.name = "services"

// tag::build-layout[]
println("Root Directory: ${settings.layout.rootDirectory}")
println("Settings Directory: ${settings.layout.settingsDirectory}")
// end::build-layout[]

// tag::build-layout-inject[]
abstract class MyBuildLayoutPlugin @Inject constructor(private val buildLayout: BuildLayout) : Plugin<Settings> {
    override fun apply(settings: Settings) {
        println(buildLayout.rootDirectory)
    }
}

apply<MyBuildLayoutPlugin>()
// end::build-layout-inject[]
