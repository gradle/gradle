plugins {
    id("com.example.spring-boot-application")
}

group = "${group}.server-application"

dependencies {
    implementation("com.example.myproduct.user-feature:table")
    implementation("com.example.myproduct.admin-feature:config")

    implementation("org.apache.juneau:juneau-marshall")
}
