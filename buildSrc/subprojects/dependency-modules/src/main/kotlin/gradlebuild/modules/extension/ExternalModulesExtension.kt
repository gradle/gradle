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
package gradlebuild.modules.extension

import gradlebuild.modules.model.License


abstract class ExternalModulesExtension {

    val groovyVersion = "2.5.12"
    val kotlinVersion = "1.3.72"

    fun futureKotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"

    val aether_connector = "org.sonatype.aether:aether-connector-wagon"
    val ant = "org.apache.ant:ant"
    val asm = "org.ow2.asm:asm"
    val asm_commons = "org.ow2.asm:asm-commons"
    val asm_tree = "org.ow2.asm:asm-tree"
    val asm_util = "org.ow2.asm:asm-util"
    val asm_analysis = "org.ow2.asm:asm-analysis"
    val awsS3_core = "com.amazonaws:aws-java-sdk-core"
    val awsS3_s3 = "com.amazonaws:aws-java-sdk-s3"
    val awsS3_kms = "com.amazonaws:aws-java-sdk-kms"
    val bouncycastle_provider = "org.bouncycastle:bcprov-jdk15on"
    val bouncycastle_pgp = "org.bouncycastle:bcpg-jdk15on"
    val bsh = "org.apache-extras.beanshell:bsh"
    val commons_codec = "commons-codec:commons-codec"
    val commons_compress = "org.apache.commons:commons-compress"
    val commons_httpclient = "org.apache.httpcomponents:httpclient"
    val commons_io = "commons-io:commons-io"
    val commons_lang = "commons-lang:commons-lang"
    val commons_math = "org.apache.commons:commons-math3"
    val fastutil = "it.unimi.dsi:fastutil"
    val gcs = "com.google.apis:google-api-services-storage"
    val groovy = "org.gradle.groovy:groovy-all"
    val gson = "com.google.code.gson:gson"
    val guava = "com.google.guava:guava"
    val inject = "javax.inject:javax.inject"
    val gradleProfiler = "org.gradle.profiler:gradle-profiler"
    val ivy = "org.apache.ivy:ivy"
    val jackson_core = "com.fasterxml.jackson.core:jackson-core"
    val jackson_annotations = "com.fasterxml.jackson.core:jackson-annotations"
    val jackson_databind = "com.fasterxml.jackson.core:jackson-databind"
    val jansi = "org.fusesource.jansi:jansi"
    val jatl = "com.googlecode.jatl:jatl"
    val jaxb = "com.sun.xml.bind:jaxb-impl"
    val jcifs = "org.samba.jcifs:jcifs"
    val jgit = "org.eclipse.jgit:org.eclipse.jgit"
    val joda = "joda-time:joda-time"
    val jsch = "com.jcraft:jsch"
    val jsr305 = "com.google.code.findbugs:jsr305"
    val junit = "junit:junit"
    val junit_platform = "org.junit.platform:junit-platform-launcher"
    val junit5_vintage = "org.junit.vintage:junit-vintage-engine"
    val kryo = "com.esotericsoftware.kryo:kryo"
    val maven3 = "org.apache.maven:maven-core"
    val maven3_wagon_file = "org.apache.maven.wagon:wagon-file"
    val maven3_wagon_http = "org.apache.maven.wagon:wagon-http"
    val nativePlatform = "net.rubygrapefruit:native-platform"
    val nekohtml = "net.sourceforge.nekohtml:nekohtml"
    val objenesis = "org.objenesis:objenesis"
    val plexus_container = "org.codehaus.plexus:plexus-container-default"
    val plist = "com.googlecode.plist:dd-plist"
    val pmaven_common = "org.sonatype.pmaven:pmaven-common"
    val pmaven_groovy = "org.sonatype.pmaven:pmaven-groovy"
    val rhino = "org.mozilla:rhino"
    val simple = "org.simpleframework:simple"
    val snakeyaml = "org.yaml:snakeyaml"
    val testng = "org.testng:testng"
    val trove4j = "org.jetbrains.intellij.deps:trove4j"
    val xerces = "xerces:xercesImpl"
    val xmlApis = "xml-apis:xml-apis"
    val slf4j_api = "org.slf4j:slf4j-api"
    val jcl_to_slf4j = "org.slf4j:jcl-over-slf4j"
    val jul_to_slf4j = "org.slf4j:jul-to-slf4j"
    val log4j_to_slf4j = "org.slf4j:log4j-over-slf4j"
    val ansi_control_sequence_util = "net.rubygrapefruit:ansi-control-sequence-util"

    // these are transitive dependencies that are part of the Gradle distribution
    val jetbrains_annotations = "org.jetbrains:annotations"
    val ant_launcher = "org.apache.ant:ant-launcher"
    val minlog = "com.esotericsoftware.minlog:minlog"
    val aether_api = "org.sonatype.aether:aether-api"
    val aether_impl = "org.sonatype.aether:aether-impl"
    val aether_spi = "org.sonatype.aether:aether-spi"
    val aether_util = "org.sonatype.aether:aether-util"
    val google_api_client = "com.google.api-client:google-api-client"
    val google_http_client = "com.google.http-client:google-http-client"
    val google_http_client_jackson2 = "com.google.http-client:google-http-client-jackson2"
    val google_oauth_client = "com.google.oauth-client:google-oauth-client"
    val hamcrest = "org.hamcrest:hamcrest-core"
    val httpcore = "org.apache.httpcomponents:httpcore"
    val jcommander = "com.beust:jcommander"
    val jzlib = "com.jcraft:jzlib"
    val maven3_aether_provider = "org.apache.maven:maven-aether-provider"
    val maven3_artifact = "org.apache.maven:maven-artifact"
    val maven3_compat = "org.apache.maven:maven-compat"
    val maven3_model = "org.apache.maven:maven-model"
    val maven3_model_builder = "org.apache.maven:maven-model-builder"
    val maven3_plugin_api = "org.apache.maven:maven-plugin-api"
    val maven3_repository_metadata = "org.apache.maven:maven-repository-metadata"
    val maven3_settings = "org.apache.maven:maven-settings"
    val maven3_settings_builder = "org.apache.maven:maven-settings-builder"
    val plexus_cipher = "org.sonatype.plexus:plexus-cipher"
    val plexus_classworlds = "org.codehaus.plexus:plexus-classworlds"
    val plexus_component_annotations = "org.codehaus.plexus:plexus-component-annotations"
    val plexus_interpolation = "org.codehaus.plexus:plexus-interpolation"
    val plexus_sec_dispatcher = "org.codehaus.plexus:plexus-sec-dispatcher"
    val plexus_utils = "org.codehaus.plexus:plexus-utils"
    val maven3_wagon_http_shared = "org.apache.maven.wagon:wagon-http-shared"
    val maven3_wagon_provider_api = "org.apache.maven.wagon:wagon-provider-api"
    val xbean_reflect = "org.apache.xbean:xbean-reflect"

    // Test classpath only libraries
    val spock = "org.spockframework:spock-core"
    val bytebuddy = "net.bytebuddy:byte-buddy"
    val jsoup = "org.jsoup:jsoup"
    val xmlunit = "xmlunit:xmlunit"
    val jetty = "org.mortbay.jetty:jetty"
    val mina = "org.apache.mina:mina-core"
    val sshdCore = "org.apache.sshd:sshd-core"
    val sshdScp = "org.apache.sshd:sshd-scp"
    val sshdSftp = "org.apache.sshd:sshd-sftp"
    val cglib = "cglib:cglib"
    val sampleCheck = "org.gradle:sample-check"
    val littleproxy = "org.gradle.org.littleshoot:littleproxy"
    val mockito_kotlin = "com.nhaarman:mockito-kotlin"
    val mockito_kotlin2 = "com.nhaarman.mockitokotlin2:mockito-kotlin"
    val jackson_kotlin = "com.fasterxml.jackson.module:jackson-module-kotlin"
    val archunit = "com.tngtech.archunit:archunit"
    val archunit_junit4 = "com.tngtech.archunit:archunit-junit4"
    val testcontainers_spock = "org.testcontainers:spock"
    val httpmime = "org.apache.httpcomponents:httpmime"

    // TODO because("")

    val licenses = mapOf(
        aether_connector to License.EPL,
        ant to License.Apache2,
        asm to License.BSD3,
        asm_commons to License.BSD3,
        asm_tree to License.BSD3,
        asm_util to License.BSD3,
        asm_analysis to License.BSD3,
        awsS3_core to License.Apache2,
        awsS3_s3 to License.Apache2,
        awsS3_kms to License.Apache2,
        bouncycastle_provider to License.MIT,
        bouncycastle_pgp to License.MIT,
        bsh to License.Apache2,
        commons_codec to License.Apache2,
        commons_compress to License.Apache2,
        commons_httpclient to License.Apache2,
        commons_io to License.Apache2,
        commons_lang to License.Apache2,
        commons_math to License.Apache2,
        fastutil to License.Apache2,
        gcs to License.Apache2,
        groovy to License.Apache2,
        gson to License.Apache2,
        guava to License.Apache2,
        inject to License.Apache2,
        gradleProfiler to License.Apache2,
        ivy to License.Apache2,
        jackson_core to License.Apache2,
        jackson_annotations to License.Apache2,
        jackson_databind to License.Apache2,
        jansi to License.Apache2,
        jatl to License.Apache2,
        jaxb to License.CDDL,
        jcifs to License.LGPL21,
        jgit to License.EDL,
        joda to License.Apache2,
        jsch to License.BSDStyle,
        jsr305 to License.BSD3,
        junit to License.EPL,
        junit_platform to License.EPL,
        junit5_vintage to License.EPL,
        kryo to License.BSD3,
        maven3 to License.Apache2,
        maven3_wagon_file to License.Apache2,
        maven3_wagon_http to License.Apache2,
        nativePlatform to License.Apache2,
        nekohtml to License.Apache2,
        objenesis to License.Apache2,
        plexus_container to License.Apache2,
        plist to License.MIT,
        pmaven_common to License.Apache2,
        pmaven_groovy to License.Apache2,
        rhino to License.MPL2,
        simple to License.Apache2,
        snakeyaml to License.Apache2,
        testng to License.Apache2,
        trove4j to License.LGPL21,
        xerces to License.Apache2,
        xmlApis to License.Apache2,
        slf4j_api to License.MIT,
        jcl_to_slf4j to License.MIT,
        jul_to_slf4j to License.MIT,
        log4j_to_slf4j to License.MIT,
        ansi_control_sequence_util to License.Apache2,

        jetbrains_annotations to License.Apache2,
        ant_launcher to License.Apache2,
        minlog to License.BSD3,
        aether_api to License.EPL,
        aether_impl to License.EPL,
        aether_spi to License.EPL,
        aether_util to License.EPL,
        google_api_client to License.Apache2,
        google_http_client to License.Apache2,
        google_http_client_jackson2 to License.Apache2,
        google_oauth_client to License.Apache2,
        hamcrest to License.BSD3,
        httpcore to License.Apache2,
        jcommander to License.Apache2,
        jzlib to License.BSDStyle,
        maven3_aether_provider to License.Apache2,
        maven3_artifact to License.Apache2,
        maven3_compat to License.Apache2,
        maven3_model to License.Apache2,
        maven3_model_builder to License.Apache2,
        maven3_plugin_api to License.Apache2,
        maven3_repository_metadata to License.Apache2,
        maven3_settings to License.Apache2,
        maven3_settings_builder to License.Apache2,
        plexus_cipher to License.Apache2,
        plexus_classworlds to License.Apache2,
        plexus_component_annotations to License.Apache2,
        plexus_interpolation to License.Apache2,
        plexus_sec_dispatcher to License.Apache2,
        plexus_utils to License.Apache2,
        maven3_wagon_http_shared to License.Apache2,
        maven3_wagon_provider_api to License.Apache2,
        xbean_reflect to License.Apache2
    )
}
