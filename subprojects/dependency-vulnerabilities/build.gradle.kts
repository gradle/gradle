/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.expediagroup.graphql.plugin.generator.GraphQLClientType

plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("com.expediagroup.graphql") version "4.0.0-alpha.7"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":base-services"))
    implementation(project(":dependency-vulnerabilities-api"))

    implementation(libs.graphQl)
    implementation(libs.guava)
    implementation(libs.futureKotlin("reflect"))
    implementation(libs.futureKotlin("stdlib-jdk7"))
    implementation("io.ktor:ktor-client-okhttp:1.3.1")
    implementation("io.ktor:ktor-client-logging-jvm:1.3.1")
}

val graphqlGenerateClient by tasks.getting(com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask::class) {
    clientType.set(GraphQLClientType.KTOR)
    packageName.set("org.gradle.internal.vulnerability.provider.github.generated")
    val graphQlSchema = file("src/main/resources/github_schema.graphql")
    schemaFile.fileValue(graphQlSchema)
}
