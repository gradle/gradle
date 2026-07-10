abstract class MyBuildLayoutPlugin @Inject constructor(private val buildLayout: BuildLayout) : Plugin<Settings> {
    override fun apply(settings: Settings) {
        println(buildLayout.rootDirectory)
    }
}

apply<MyBuildLayoutPlugin>()
