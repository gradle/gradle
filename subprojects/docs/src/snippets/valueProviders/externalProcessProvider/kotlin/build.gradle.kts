val gitVersion = providers.exec {
    commandLine("git", "--version")
}.standardOutput.asText.get()
