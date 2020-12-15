plugins {
    kotlin("multiplatform")
}

kotlin {
    macosX64 {
        binaries {
            executable()
        }
    }
    linuxX64 {
        binaries {
            executable()
        }
    }
}

dependencies {
    "commonMainImplementation"(kotlin("stdlib-common"))
    "commonMainImplementation"("example:kotlin-mpp-library:1.0")
    "commonMainImplementation"("example:kotlin-mpp-android-library:1.0")
}
