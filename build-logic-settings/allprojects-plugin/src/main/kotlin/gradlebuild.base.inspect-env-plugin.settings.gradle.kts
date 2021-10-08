val destinationFile = File("build/report-ge-env.txt")
destinationFile.parentFile.mkdirs()
destinationFile.writeText(System.getenv("GRADLE_ENTERPRISE_ACCESS_KEY") ?: "")
