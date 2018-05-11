enableFeaturePreview("STABLE_PUBLISHING")

rootProject.name = "gradle-kotlin-dsl"

include(
    "provider",
    "provider-spi",
    "provider-plugins",
    "tooling-models",
    "tooling-builders",
    "plugins",
    "plugins-experiments",
    "test-fixtures",
    "samples-tests",
    "integ-tests")
