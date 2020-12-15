plugins {
    `kotlin-dsl`
    `groovy-gradle-plugin`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("com.gradle.enterprise:com.gradle.enterprise.gradle.plugin:3.5")
    implementation("com.gradle.enterprise.gradle-enterprise-conventions-plugin:com.gradle.enterprise.gradle-enterprise-conventions-plugin.gradle.plugin:0.7.1")
}
