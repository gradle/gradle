/*
 * Copyright 2018 the original author or authors.
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

/**
 * This project provides the "platform" for the Gradle distribution.
 * We want the versions that are packaged in the distribution to be used everywhere (e.g. in all test scenarios)
 * Hence, we lock the versions down here for all other subprojects.
 *
 * Note:
 * We use strictly here because we do not have any better means to do this at the moment.
 * Ideally we wound be able to say "lock down all the versions of the dependencies resolved for the distribution"
 */
plugins {
    id("gradlebuild.platform")
}

val aetherVersion = "1.13.1"
val antVersion = "1.10.8"
val archunitVersion = "0.11.0"
val asmVersion = "7.3.1"
val awsS3Version = "1.11.633"
val bouncycastleVersion = "1.64"
val googelApiVersion = "1.25.0"
val jacksonVersion = "2.10.2"
val mavenVersion = "3.0.5"
val mavenWagonVersion = "3.0.0"
val pmavenVersion = "0.8-20100325"
val slf4jVersion = "1.7.28"
val sshdVersion = "2.0.0"

dependencies {
    constraints {
        api(libs.aether_api)            { version { strictly(aetherVersion) }}
        api(libs.aether_connector)      { version { strictly(aetherVersion) }}
        api(libs.aether_impl)           { version { strictly(aetherVersion) }}
        api(libs.aether_spi)            { version { strictly(aetherVersion) }}
        api(libs.aether_util)           { version { strictly(aetherVersion) }}
        api(libs.ansi_control_sequence_util) { version { strictly("0.2") }}
        api(libs.ant)                   { version { strictly(antVersion) }}
        api(libs.ant_launcher)          { version { strictly(antVersion) }}
        api(libs.asm)                   { version { strictly(asmVersion) }}
        api(libs.asm_analysis)          { version { strictly(asmVersion) }}
        api(libs.asm_commons)           { version { strictly(asmVersion) }}
        api(libs.asm_tree)              { version { strictly(asmVersion) }}
        api(libs.asm_util)              { version { strictly(asmVersion) }}
        api(libs.awsS3_core)            { version { strictly(awsS3Version) }}
        api(libs.awsS3_kms)             { version { strictly(awsS3Version) }}
        api(libs.awsS3_s3)              { version { strictly(awsS3Version) }}
        api(libs.bouncycastle_pgp)      { version { strictly(bouncycastleVersion) }}
        api(libs.bouncycastle_provider) { version { strictly(bouncycastleVersion) }}
        api(libs.bsh)                   { version { strictly("2.0b6") }}
        api(libs.commons_codec)         { version { strictly("1.13") }}
        api(libs.commons_compress)      { version { strictly("1.19") }}
        api(libs.commons_httpclient)    { version { strictly("4.5.10") }}
        api(libs.commons_io)            { version { strictly("2.6") }}
        api(libs.commons_lang)          { version { strictly("2.6") }}
        api(libs.commons_math)          { version { strictly("3.6.1") }}
        api(libs.fastutil)              { version { strictly("8.3.0") }}
        api(libs.gcs)                   { version { strictly("v1-rev136-1.25.0") }}
        api(libs.google_api_client)     { version { strictly(googelApiVersion) }}
        api(libs.google_http_client)    { version { strictly(googelApiVersion) }}
        api(libs.google_http_client_jackson2) { version { strictly(googelApiVersion) }}
        api(libs.google_oauth_client)   { version { strictly(googelApiVersion) }}
        api(libs.gradleProfiler)        { version { strictly("0.11.0") }}
        api(libs.groovy)                { version { strictly("1.3-${libs.groovyVersion}"); because("emulating the Groovy 2.4-style groovy-all.jar, see https://github.com/gradle/gradle-groovy-all") }}
        api(libs.gson)                  { version { strictly("2.8.5") }}
        api(libs.guava)                 { version { strictly("27.1-android"); because("JRE variant introduces regression - https://github.com/google/guava/issues/3223") }}
        api(libs.hamcrest)              { version { strictly("1.3") }}
        api(libs.httpcore)              { version { strictly("4.4.12") }}
        api(libs.inject)                { version { strictly("1") }}
        api(libs.ivy)                   { version { strictly("2.3.0"); because("2.4.0 contains a breaking change in DefaultModuleDescriptor.getExtraInfo(), cf. https://issues.apache.org/jira/browse/IVY-1457") }}
        api(libs.jackson_annotations)   { version { strictly(jacksonVersion) }}
        api(libs.jackson_core)          { version { strictly(jacksonVersion) }}
        api(libs.jackson_databind)      { version { strictly(jacksonVersion) }}
        api(libs.jansi)                 { version { strictly("1.18") }}
        api(libs.jatl)                  { version { strictly("0.2.3") }}
        api(libs.jaxb)                  { version { strictly("2.3.2") }}
        api(libs.jcifs)                 { version { strictly("1.3.17") }}
        api(libs.jcl_to_slf4j)          { version { strictly(slf4jVersion) }}
        api(libs.jcommander)            { version { strictly("1.72") }}
        api(libs.jetbrains_annotations) { version { strictly("13.0") }}
        api(libs.jgit)                  { version { strictly("5.7.0.202003110725-r") }}
        api(libs.joda)                  { version { strictly("2.10.4") }}
        api(libs.jsch)                  { version { strictly("0.1.55") }}
        api(libs.jsr305)                { version { strictly("3.0.2") }}
        api(libs.jul_to_slf4j)          { version { strictly(slf4jVersion) }}
        api(libs.junit)                 { version { strictly("4.13") }}
        api(libs.junit5_vintage)        { version { strictly("5.6.2") }}
        api(libs.junit_platform)        { version { strictly("1.6.2") }}
        api(libs.jzlib)                 { version { strictly("1.1.3") }}
        api(libs.kryo)                  { version { strictly("2.24.0") }}
        api(libs.log4j_to_slf4j)        { version { strictly(slf4jVersion) }}
        api(libs.maven3)                { version { strictly(mavenVersion) }}
        api(libs.maven3_aether_provider) { version { strictly(mavenVersion) }}
        api(libs.maven3_artifact)       { version { strictly(mavenVersion) }}
        api(libs.maven3_compat)         { version { strictly(mavenVersion) }}
        api(libs.maven3_model)          { version { strictly(mavenVersion) }}
        api(libs.maven3_model_builder)  { version { strictly(mavenVersion) }}
        api(libs.maven3_plugin_api)     { version { strictly(mavenVersion) }}
        api(libs.maven3_repository_metadata) { version { strictly(mavenVersion) }}
        api(libs.maven3_settings)       { version { strictly(mavenVersion) }}
        api(libs.maven3_settings_builder) { version { strictly(mavenVersion) }}
        api(libs.maven3_wagon_file)     { version { strictly(mavenWagonVersion) }}
        api(libs.maven3_wagon_http)     { version { strictly(mavenWagonVersion); because("3.1.0 of wagon-http seems to break Digest authentication")  }}
        api(libs.maven3_wagon_http_shared) { version { strictly(mavenWagonVersion) }}
        api(libs.maven3_wagon_provider_api) { version { strictly(mavenWagonVersion) }}
        api(libs.minlog)                { version { strictly("1.2") }}
        api(libs.nativePlatform)        { version { strictly("0.22-snapshot-20200626124009+0000") }}
        api(libs.nekohtml)              { version { strictly("1.9.22") }}
        api(libs.objenesis)             { version { strictly("2.6") }}
        api(libs.plexus_cipher)         { version { strictly("1.7") }}
        api(libs.plexus_classworlds)    { version { strictly("2.5.1") }}
        api(libs.plexus_component_annotations) { version { strictly("1.5.5") }}
        api(libs.plexus_container)      { version { strictly("1.7.1") }}
        api(libs.plexus_interpolation)  { version { strictly("1.14") }}
        api(libs.plexus_sec_dispatcher) { version { strictly("1.3") }}
        api(libs.plexus_utils)          { version { strictly("3.1.0") }}
        api(libs.plist)                 { version { strictly("1.21") }}
        api(libs.pmaven_common)         { version { strictly(pmavenVersion) }}
        api(libs.pmaven_groovy)         { version { strictly(pmavenVersion) }}
        api(libs.rhino)                 { version { strictly("1.7.10") }}
        api(libs.simple)                { version { strictly("4.1.21") }}
        api(libs.slf4j_api)             { version { strictly(slf4jVersion) }}
        api(libs.snakeyaml)             { version { strictly("1.17") }}
        api(libs.testng)                { version { strictly("6.3.1"); because("later versions break test cross-version test filtering") }}
        api(libs.trove4j)               { version { strictly("1.0.20181211") }}
        api(libs.xbean_reflect)         { version { strictly("3.7") }}
        api(libs.xerces)                { version { strictly("2.12.0") }}
        api(libs.xmlApis)               { version { strictly("1.4.01"); because("2.0.x has a POM with relocation Gradle does not handle well") }}

        // test only
        api(libs.archunit)              { version { strictly(archunitVersion) }}
        api(libs.archunit_junit4)       { version { strictly(archunitVersion) }}
        api(libs.bytebuddy)             { version { strictly("1.8.21") }}
        api(libs.cglib)                 { version { strictly("3.2.6") }}
        api(libs.httpmime)              { version { strictly("4.5.10") }}
        api(libs.jackson_kotlin)        { version { strictly("2.9.2") }}
        api(libs.jetty)                 { version { strictly("6.1.26") }}
        api(libs.jsoup)                 { version { strictly("1.11.3") }}
        api(libs.littleproxy)           { version { strictly("1.1.3"); because("latest officially released version is incompatible with Guava >= 20") }}
        api(libs.mina)                  { version { strictly("2.0.17") }}
        api(libs.mockito_kotlin)        { version { strictly("1.6.0") }}
        api(libs.mockito_kotlin2)       { version { strictly("2.1.0") }}
        api(libs.sampleCheck)           { version { strictly("0.12.6") }}
        api(libs.spock)                 { version { strictly("1.3-groovy-2.5") }}
        api(libs.sshdCore)              { version { strictly(sshdVersion) }}
        api(libs.sshdScp)               { version { strictly(sshdVersion) }}
        api(libs.sshdSftp)              { version { strictly(sshdVersion) }}
        api(libs.testcontainers_spock)  { version { strictly("1.12.5") }}
        api(libs.xmlunit)               { version { strictly("1.6") }}
    }
}
