plugins {
    `kotlin-dsl`
    `groovy-gradle-plugin`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("com.gradle:gradle-enterprise-gradle-plugin:3.5.2")
    implementation("com.gradle.enterprise:gradle-enterprise-conventions-plugin:0.7.2")
}
