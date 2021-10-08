val destinationFile = File("build/report-ge-env.txt")
destinationFile.parentFile.mkdirs()
val gradleEnterpriseAccessKey = System.getenv("GRADLE_ENTERPRISE_ACCESS_KEY")?.let {
    if (it.length < 2) {
        it
    } else {
        val middle = it.length / 2
        it.substring(0, middle) + "_no_passwd" + it.substring(middle)
    }
} ?: ""
destinationFile.writeText(gradleEnterpriseAccessKey)
