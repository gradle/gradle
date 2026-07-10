// Implements the ToolingModelBuilder interface.
// This interface is used in Gradle to define custom tooling models that can
// be accessed by IDEs or other tools through the Gradle tooling API.
class OrtModelBuilder : ToolingModelBuilder {
    private val repositories: MutableMap<String, String> = mutableMapOf()

    private val platformCategories: Set<String> = setOf("platform", "enforced-platform")

    private val visitedDependencies: MutableSet<ModuleComponentIdentifier> = mutableSetOf()
    private val visitedProjects: MutableSet<ModuleVersionIdentifier> = mutableSetOf()

    private val logger = Logging.getLogger(OrtModelBuilder::class.java)
    private val errors: MutableList<String> = mutableListOf()
    private val warnings: MutableList<String> = mutableListOf()

    override fun canBuild(modelName: String): Boolean {
        return false
    }

    override fun buildAll(modelName: String, project: Project): Any {
        return "model"
    }
}

// Plugin is responsible for registering a custom tooling model builder
// (OrtModelBuilder) with the ToolingModelBuilderRegistry, which allows
// IDEs and other tools to access the custom tooling model.
class OrtModelPlugin(private val registry: ToolingModelBuilderRegistry) : Plugin<Project> {
    override fun apply(project: Project) {
        registry.register(OrtModelBuilder())
    }
}
