rootProject.name = "toolchain-management"

// tag::toolchain-management[]
plugins {
    id("org.gradle.adopt-open-jdk-repo") version "1.0"
    id("com.azul.zulu-toolchain-management") version "0.6"
} // <1>

toolchainManagement {
    jdks { // <2>
        add("com_azul_zulu") // <3>
            {
                credentials {
                    username = "user"
                    password = "password"
                }
                authentication {
                    create<DigestAuthentication>("digest")
                }
            } // <4>
        add("org_gradle_adopt_open_jdk") // <5>
    }
}
// end::toolchain-management[]
