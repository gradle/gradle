configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability("log4j:log4j") {
        val toBeSelected = candidates.firstOrNull { it.id.let { id -> id is ModuleComponentIdentifier && id.module == "log4j-over-slf4j" } }
        if (toBeSelected != null) {
            select(toBeSelected)
        }
        because("use slf4j in place of log4j")
    }
}
