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


abstract class ExternalModulesExtension(isBundleGroovy4: Boolean) {

    val groovyVersion = if (isBundleGroovy4) "4.0.7" else "3.0.13"
    val configurationCacheReportVersion = "1.2"
    val kotlinVersion = "1.8.10"

    fun futureKotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"

    val ansiControlSequenceUtil = "net.rubygrapefruit:ansi-control-sequence-util"
    val ant = "org.apache.ant:ant"
    val antLauncher = "org.apache.ant:ant-launcher"
    val asm = "org.ow2.asm:asm"
    val asmAnalysis = "org.ow2.asm:asm-analysis"
    val asmCommons = "org.ow2.asm:asm-commons"
    val asmTree = "org.ow2.asm:asm-tree"
    val asmUtil = "org.ow2.asm:asm-util"
    val assertj = "org.assertj:assertj-core"
    val awsS3Core = "com.amazonaws:aws-java-sdk-core"
    val awsS3Kms = "com.amazonaws:aws-java-sdk-kms"
    val awsS3S3 = "com.amazonaws:aws-java-sdk-s3"
    val awsS3Sts = "com.amazonaws:aws-java-sdk-sts"
    val bouncycastlePgp = "org.bouncycastle:bcpg-jdk15on"
    val bouncycastlePkix = "org.bouncycastle:bcpkix-jdk15on"
    val bouncycastleProvider = "org.bouncycastle:bcprov-jdk15on"
    val bsh = "org.apache-extras.beanshell:bsh"
    val capsule = "io.usethesource:capsule"
    val commonsCodec = "commons-codec:commons-codec"
    val commonsCompress = "org.apache.commons:commons-compress"
    val commonsHttpclient = "org.apache.httpcomponents:httpclient"
    val commonsIo = "commons-io:commons-io"
    val commonsLang = "commons-lang:commons-lang"
    val commonsLang3 = "org.apache.commons:commons-lang3"
    val commonsMath = "org.apache.commons:commons-math3"
    val configurationCacheReport = "org.gradle.buildtool.internal:configuration-cache-report:$configurationCacheReportVersion"
    val fastutil = "it.unimi.dsi:fastutil"
    val gcs = "com.google.apis:google-api-services-storage"
    val googleApiClient = "com.google.api-client:google-api-client"
    val googleHttpClient = "com.google.http-client:google-http-client"
    val googleHttpClientGson = "com.google.http-client:google-http-client-gson"
    val googleHttpClientApacheV2 = "com.google.http-client:google-http-client-apache-v2"
    val googleOauthClient = "com.google.oauth-client:google-oauth-client"
    val gradleProfiler = "org.gradle.profiler:gradle-profiler"
    val gradleEnterpriseTestAnnotation = "com.gradle:gradle-enterprise-testing-annotations"
    val groovyGroup = if (isBundleGroovy4) "org.apache.groovy" else "org.codehaus.groovy"
    val groovy = "$groovyGroup:groovy"
    val groovyAnt = "$groovyGroup:groovy-ant"
    val groovyAstbuilder = "$groovyGroup:groovy-astbuilder"
    val groovyConsole = "$groovyGroup:groovy-console"
    val groovyDateUtil = "$groovyGroup:groovy-dateutil"
    val groovyDatetime = "$groovyGroup:groovy-datetime"
    val groovyDoc = "$groovyGroup:groovy-groovydoc"
    val groovyJson = "$groovyGroup:groovy-json"
    val groovyNio = "$groovyGroup:groovy-nio"
    val groovySql = "$groovyGroup:groovy-sql"
    val groovyTemplates = "$groovyGroup:groovy-templates"
    val groovyTest = "$groovyGroup:groovy-test"
    val groovyXml = "$groovyGroup:groovy-xml"
    val gson = "com.google.code.gson:gson"
    val guava = "com.google.guava:guava"
    val hamcrest = "org.hamcrest:hamcrest-core"
    val httpcore = "org.apache.httpcomponents:httpcore"
    val inject = "javax.inject:javax.inject"
    val ivy = "org.apache.ivy:ivy"
    val jacksonAnnotations = "com.fasterxml.jackson.core:jackson-annotations"
    val jacksonCore = "com.fasterxml.jackson.core:jackson-core"
    val jacksonDatabind = "com.fasterxml.jackson.core:jackson-databind"
    val jakartaActivation = "com.sun.activation:jakarta.activation"
    val jakartaXmlBind = "jakarta.xml.bind:jakarta.xml.bind-api"
    val jansi = "org.fusesource.jansi:jansi"
    val jatl = "com.googlecode.jatl:jatl"
    val jaxbCore = "com.sun.xml.bind:jaxb-core"
    val jaxbImpl = "com.sun.xml.bind:jaxb-impl"
    val jcifs = "jcifs:jcifs"
    val jclToSlf4j = "org.slf4j:jcl-over-slf4j"
    val jcommander = "com.beust:jcommander"
    val jetbrainsAnnotations = "org.jetbrains:annotations"
    val jgit = "org.eclipse.jgit:org.eclipse.jgit"
    val joda = "joda-time:joda-time"
    val jsch = "com.jcraft:jsch"
    val jsr305 = "com.google.code.findbugs:jsr305"
    val julToSlf4j = "org.slf4j:jul-to-slf4j"
    val junit = "junit:junit"
    val junit5Vintage = "org.junit.vintage:junit-vintage-engine"
    val junit5JupiterApi = "org.junit.jupiter:junit-jupiter-api"
    val junitPlatform = "org.junit.platform:junit-platform-launcher"
    val jzlib = "com.jcraft:jzlib"
    val kryo = "com.esotericsoftware.kryo:kryo"
    val log4jToSlf4j = "org.slf4j:log4j-over-slf4j"
    val maven3BuilderSupport = "org.apache.maven:maven-builder-support"
    val maven3Model = "org.apache.maven:maven-model"
    val maven3RepositoryMetadata = "org.apache.maven:maven-repository-metadata"
    val maven3Settings = "org.apache.maven:maven-settings"
    val maven3SettingsBuilder = "org.apache.maven:maven-settings-builder"
    val minlog = "com.esotericsoftware.minlog:minlog"
    val nativePlatform = "net.rubygrapefruit:native-platform"
    val nativePlatformFileEvents = "net.rubygrapefruit:file-events"
    val objenesis = "org.objenesis:objenesis"
    val plexusCipher = "org.sonatype.plexus:plexus-cipher"
    val plexusInterpolation = "org.codehaus.plexus:plexus-interpolation"
    val plexusSecDispatcher = "org.codehaus.plexus:plexus-sec-dispatcher"
    val plexusUtils = "org.codehaus.plexus:plexus-utils"
    val plist = "com.googlecode.plist:dd-plist"
    val pmavenCommon = "org.sonatype.pmaven:pmaven-common"
    val pmavenGroovy = "org.sonatype.pmaven:pmaven-groovy"
    val slf4jApi = "org.slf4j:slf4j-api"
    val snakeyaml = "org.yaml:snakeyaml"
    val testng = "org.testng:testng"
    val tomlj = "org.tomlj:tomlj"
    val trove4j = "org.jetbrains.intellij.deps:trove4j"
    val jna = "net.java.dev.jna:jna"
    val agp = "com.android.tools.build:gradle"
    val xbeanReflect = "org.apache.xbean:xbean-reflect"
    val xmlApis = "xml-apis:xml-apis"

    // Compile only dependencies (dynamically downloaded if needed)
    val maven3Compat = "org.apache.maven:maven-compat"
    val maven3PluginApi = "org.apache.maven:maven-plugin-api"

    // Test classpath only libraries
    val aircompressor = "io.airlift:aircompressor"
    val archunit = "com.tngtech.archunit:archunit"
    val archunitJunit5 = "com.tngtech.archunit:archunit-junit5"
    val awaitility = "org.awaitility:awaitility-kotlin"
    val bytebuddy = "net.bytebuddy:byte-buddy"
    val bytebuddyAgent = "net.bytebuddy:byte-buddy-agent"
    val cglib = "cglib:cglib"
    val equalsverifier = "nl.jqno.equalsverifier:equalsverifier"
    val hikariCP = "com.zaxxer:HikariCP"
    val guice = "com.google.inject:guice"
    val httpmime = "org.apache.httpcomponents:httpmime"
    val jacksonKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin"
    val javaParser = "com.github.javaparser:javaparser-core"
    val jetty = "org.eclipse.jetty:jetty-http"
    val jettySecurity = "org.eclipse.jetty:jetty-security"
    val jettyWebApp = "org.eclipse.jetty:jetty-webapp"
    val joptSimple = "net.sf.jopt-simple:jopt-simple"
    val jsoup = "org.jsoup:jsoup"
    val jtar = "org.kamranzafar:jtar"
    val kotlinCoroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core"
    val kotlinCoroutinesDebug = "org.jetbrains.kotlinx:kotlinx-coroutines-debug"
    val littleproxy = "xyz.rogfam:littleproxy"
    val mina = "org.apache.mina:mina-core"
    val mockitoCore = "org.mockito:mockito-core"
    val mockitoKotlin = "com.nhaarman:mockito-kotlin"
    val mockitoKotlin2 = "com.nhaarman.mockitokotlin2:mockito-kotlin"
    val mySqlConnector = "mysql:mysql-connector-java"
    val samplesCheck = "org.gradle.exemplar:samples-check"
    val snappy = "org.iq80.snappy:snappy"
    val servletApi = "javax.servlet:javax.servlet-api"
    val socksProxy = "com.github.bbottema:java-socks-proxy-server"
    val spock = "org.spockframework:spock-core"
    val spockJUnit4 = "org.spockframework:spock-junit4"
    val sshdCore = "org.apache.sshd:sshd-core"
    val sshdScp = "org.apache.sshd:sshd-scp"
    val sshdSftp = "org.apache.sshd:sshd-sftp"
    val testcontainersSpock = "org.testcontainers:spock"
    val typesafeConfig = "com.typesafe:config"
    val xerces = "xerces:xercesImpl"
    val xmlunit = "xmlunit:xmlunit"

    val licenses = mapOf(
        ansiControlSequenceUtil to License.Apache2,
        ant to License.Apache2,
        antLauncher to License.Apache2,
        asm to License.BSD3,
        asmAnalysis to License.BSD3,
        asmCommons to License.BSD3,
        asmTree to License.BSD3,
        asmUtil to License.BSD3,
        assertj to License.Apache2,
        awsS3Core to License.Apache2,
        awsS3Kms to License.Apache2,
        awsS3S3 to License.Apache2,
        awsS3Sts to License.Apache2,
        bouncycastlePgp to License.MIT,
        bouncycastleProvider to License.MIT,
        bsh to License.Apache2,
        capsule to License.BSDStyle,
        commonsCodec to License.Apache2,
        commonsCompress to License.Apache2,
        commonsHttpclient to License.Apache2,
        commonsIo to License.Apache2,
        commonsLang to License.Apache2,
        commonsLang3 to License.Apache2,
        commonsMath to License.Apache2,
        configurationCacheReport to License.Apache2,
        fastutil to License.Apache2,
        gcs to License.Apache2,
        googleApiClient to License.Apache2,
        googleHttpClient to License.Apache2,
        googleHttpClientGson to License.Apache2,
        googleHttpClientApacheV2 to License.Apache2,
        googleOauthClient to License.Apache2,
        gradleProfiler to License.Apache2,
        groovy to License.Apache2,
        gson to License.Apache2,
        guava to License.Apache2,
        hamcrest to License.BSD3,
        httpcore to License.Apache2,
        hikariCP to License.Apache2,
        inject to License.Apache2,
        ivy to License.Apache2,
        jacksonAnnotations to License.Apache2,
        jacksonCore to License.Apache2,
        jacksonDatabind to License.Apache2,
        jakartaActivation to License.EDL,
        jakartaXmlBind to License.EDL,
        jansi to License.Apache2,
        jatl to License.Apache2,
        jaxbCore to License.EDL,
        jaxbImpl to License.EDL,
        jcifs to License.LGPL21,
        jclToSlf4j to License.MIT,
        jcommander to License.Apache2,
        jetbrainsAnnotations to License.Apache2,
        jgit to License.EDL,
        joda to License.Apache2,
        jsch to License.BSDStyle,
        jsr305 to License.BSD3,
        julToSlf4j to License.MIT,
        junit to License.EPL,
        junit5Vintage to License.EPL,
        junit5JupiterApi to License.EPL,
        junitPlatform to License.EPL,
        jzlib to License.BSDStyle,
        kryo to License.BSD3,
        log4jToSlf4j to License.MIT,
        maven3BuilderSupport to License.Apache2,
        maven3Model to License.Apache2,
        maven3RepositoryMetadata to License.Apache2,
        maven3Settings to License.Apache2,
        maven3SettingsBuilder to License.Apache2,
        minlog to License.BSD3,
        nativePlatform to License.Apache2,
        nativePlatformFileEvents to License.Apache2,
        objenesis to License.Apache2,
        plexusCipher to License.Apache2,
        plexusInterpolation to License.Apache2,
        plexusSecDispatcher to License.Apache2,
        plexusUtils to License.Apache2,
        plist to License.MIT,
        pmavenCommon to License.Apache2,
        pmavenGroovy to License.Apache2,
        slf4jApi to License.MIT,
        snakeyaml to License.Apache2,
        testng to License.Apache2,
        tomlj to License.Apache2,
        trove4j to License.LGPL21,
        xbeanReflect to License.Apache2,
        xmlApis to License.Apache2
    )
}
