plugins {
    id("com.gradle.build-scan") version "1.6"
}

buildScan {
    setLicenseAgreementUrl("https://gradle.com/terms-of-service")
    setLicenseAgree("yes")
}
