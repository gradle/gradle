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
val antVersion = "1.10.9"
val archunitVersion = "0.11.0"
val asmVersion = "7.3.1"
val awsS3Version = "1.11.633"
val bouncycastleVersion = "1.64"
val googleApiVersion = "1.25.0"
val jacksonVersion = "2.10.2"
val jettyVersion = "9.4.31.v20200723"
val mavenVersion = "3.0.5"
val mavenWagonVersion = "3.0.0"
val nativePlatformVersion = "0.22-milestone-10"
val pmavenVersion = "0.8-20100325"
val slf4jVersion = "1.7.28"
val sshdVersion = "2.0.0"
val tomljVersion = "1.0.0"

dependencies {
    constraints {
        api(libs.aetherApi)             { version { strictly(aetherVersion) }}
        api(libs.aetherConnector)       { version { strictly(aetherVersion) }}
        api(libs.aetherImpl)            { version { strictly(aetherVersion) }}
        api(libs.aetherSpi)             { version { strictly(aetherVersion) }}
        api(libs.aetherUtil)            { version { strictly(aetherVersion) }}
        api(libs.ansiControlSequenceUtil) { version { strictly("0.2") }}
        api(libs.ant)                   { version { strictly(antVersion) }}
        api(libs.antLauncher)           { version { strictly(antVersion) }}
        api(libs.asm)                   { version { strictly(asmVersion) }}
        api(libs.asmAnalysis)           { version { strictly(asmVersion) }}
        api(libs.asmCommons)            { version { strictly(asmVersion) }}
        api(libs.asmTree)               { version { strictly(asmVersion) }}
        api(libs.asmUtil)               { version { strictly(asmVersion) }}
        api(libs.awsS3Core)             { version { strictly(awsS3Version) }}
        api(libs.awsS3Kms)              { version { strictly(awsS3Version) }}
        api(libs.awsS3S3)               { version { strictly(awsS3Version) }}
        api(libs.awsS3Sts)              { version { strictly(awsS3Version) }}
        api(libs.bouncycastlePgp)       { version { strictly(bouncycastleVersion) }}
        api(libs.bouncycastleProvider)  { version { strictly(bouncycastleVersion) }}
        api(libs.bsh)                   { version { strictly("2.0b6") }}
        api(libs.commonsCodec)          { version { strictly("1.13") }}
        api(libs.commonsCompress)       { version { strictly("1.19") }}
        api(libs.commonsHttpclient)     { version { strictly("4.5.10") }}
        api(libs.commonsIo)             { version { strictly("2.6") }}
        api(libs.commonsLang)           { version { strictly("2.6") }}
        api(libs.commonsMath)           { version { strictly("3.6.1") }}
        api(libs.fastutil)              { version { strictly("8.3.0") }}
        api(libs.gcs)                   { version { strictly("v1-rev136-1.25.0") }}
        api(libs.googleApiClient)       { version { strictly(googleApiVersion) }}
        api(libs.googleHttpClient)      { version { strictly(googleApiVersion) }}
        api(libs.googleHttpClientJackson2) { version { strictly(googleApiVersion) }}
        api(libs.googleOauthClient)     { version { strictly(googleApiVersion) }}
        api(libs.gradleProfiler)        { version { strictly("0.15.0") }}
        api(libs.groovy)                { version { strictly("1.3-${libs.groovyVersion}"); because("emulating the Groovy 2.4-style groovy-all.jar, see https://github.com/gradle/gradle-groovy-all") }}
        api(libs.gson)                  { version { strictly("2.8.5") }}
        api(libs.guava)                 { version { strictly("27.1-android"); because("JRE variant introduces regression - https://github.com/google/guava/issues/3223") }}
        api(libs.hamcrest)              { version { strictly("1.3") }}
        api(libs.hikariCP)              { version { strictly("3.4.5") }}
        api(libs.httpcore)              { version { strictly("4.4.12") }}
        api(libs.inject)                { version { strictly("1") }}
        api(libs.ivy)                   { version { strictly("2.3.0"); because("2.4.0 contains a breaking change in DefaultModuleDescriptor.getExtraInfo(), cf. https://issues.apache.org/jira/browse/IVY-1457") }}
        api(libs.jacksonAnnotations)    { version { strictly(jacksonVersion) }}
        api(libs.jacksonCore)           { version { strictly(jacksonVersion) }}
        api(libs.jacksonDatabind)       { version { strictly(jacksonVersion) }}
        api(libs.jansi)                 { version { strictly("1.18") }}
        api(libs.jatl)                  { version { strictly("0.2.3") }}
        api(libs.jaxb)                  { version { strictly("2.3.2") }}
        api(libs.jcifs)                 { version { strictly("1.3.17") }}
        api(libs.jclToSlf4j)            { version { strictly(slf4jVersion) }}
        api(libs.jcommander)            { version { strictly("1.72") }}
        api(libs.jetbrainsAnnotations)  { version { strictly("13.0") }}
        api(libs.jgit)                  { version { strictly("5.7.0.202003110725-r") }}
        api(libs.joda)                  { version { strictly("2.10.4") }}
        api(libs.joptSimple)            { version { strictly("5.0.4"); because("needed to create profiler in Gradle profiler API") }}
        api(libs.jsch)                  { version { strictly("0.1.55") }}
        api(libs.jsr305)                { version { strictly("3.0.2") }}
        api(libs.julToSlf4j)            { version { strictly(slf4jVersion) }}
        api(libs.junit)                 { version { strictly("4.13") }}
        api(libs.junit5Vintage)         { version { strictly("5.7.0") }}
        api(libs.junitPlatform)         { version { strictly("1.7.0") }}
        api(libs.jzlib)                 { version { strictly("1.1.3") }}
        api(libs.kryo)                  { version { strictly("2.24.0") }}
        api(libs.log4jToSlf4j)          { version { strictly(slf4jVersion) }}
        api(libs.maven3)                { version { strictly(mavenVersion) }}
        api(libs.maven3AetherProvider)  { version { strictly(mavenVersion) }}
        api(libs.maven3Artifact)        { version { strictly(mavenVersion) }}
        api(libs.maven3Compat)          { version { strictly(mavenVersion) }}
        api(libs.maven3Model)           { version { strictly(mavenVersion) }}
        api(libs.maven3ModelBuilder)    { version { strictly(mavenVersion) }}
        api(libs.maven3PluginApi)       { version { strictly(mavenVersion) }}
        api(libs.maven3RepositoryMetadata) { version { strictly(mavenVersion) }}
        api(libs.maven3Settings)        { version { strictly(mavenVersion) }}
        api(libs.maven3SettingsBuilder) { version { strictly(mavenVersion) }}
        api(libs.maven3WagonFile)       { version { strictly(mavenWagonVersion) }}
        api(libs.maven3WagonHttp)       { version { strictly(mavenWagonVersion); because("3.1.0 of wagon-http seems to break Digest authentication")  }}
        api(libs.maven3WagonHttpShared) { version { strictly(mavenWagonVersion) }}
        api(libs.maven3WagonProviderApi) { version { strictly(mavenWagonVersion) }}
        api(libs.minlog)                { version { strictly("1.2") }}
        api(libs.nativePlatform)        { version { strictly(nativePlatformVersion) }}
        api(libs.nativePlatformFileEvents) { version { strictly(nativePlatformVersion) }}
        api(libs.nekohtml)              { version { strictly("1.9.22") }}
        api(libs.objenesis)             { version { strictly("2.6") }}
        api(libs.plexusCipher)          { version { strictly("1.7") }}
        api(libs.plexusClassworlds)     { version { strictly("2.5.1") }}
        api(libs.plexusComponentAnnotations) { version { strictly("1.5.5") }}
        api(libs.plexusContainer)       { version { strictly("1.7.1") }}
        api(libs.plexusInterpolation)   { version { strictly("1.14") }}
        api(libs.plexusSecDispatcher)   { version { strictly("1.3") }}
        api(libs.plexusUtils)           { version { strictly("3.1.0") }}
        api(libs.plist)                 { version { strictly("1.21") }}
        api(libs.pmavenCommon)          { version { strictly(pmavenVersion) }}
        api(libs.pmavenGroovy)          { version { strictly(pmavenVersion) }}
        api(libs.servletApi)            { version { strictly("3.1.0") }}
        api(libs.slf4jApi)              { version { strictly(slf4jVersion) }}
        api(libs.snakeyaml)             { version { strictly("1.17") }}
        api(libs.testng)                { version { strictly("6.3.1"); because("later versions break test cross-version test filtering") }}
        api(libs.tomlj)                 { version { strictly(tomljVersion) }}
        api(libs.trove4j)               { version { strictly("1.0.20181211") }}
        api(libs.xbeanReflect)          { version { strictly("3.7") }}
        api(libs.xerces)                { version { strictly("2.12.0") }}
        api(libs.xmlApis)               { version { strictly("1.4.01"); because("2.0.x has a POM with relocation Gradle does not handle well") }}

        // test only
        api(libs.aircompressor)         { version { strictly("0.8") }}
        api(libs.archunit)              { version { strictly(archunitVersion) }}
        api(libs.archunitJunit4)        { version { strictly(archunitVersion) }}
        api(libs.awaitility)            { version { strictly("3.1.6") }}
        api(libs.bytebuddy)             { version { strictly("1.8.21") }}
        api(libs.cglib)                 { version { strictly("3.2.6") }}
        api(libs.equalsverifier)        { version { strictly("2.1.6") }}
        api(libs.flightrecorder)        { version { strictly("7.0.0-alpha01") }}
        api(libs.guice)                 { version { strictly("2.0") }}
        api(libs.httpmime)              { version { strictly("4.5.10") }}
        api(libs.jacksonKotlin)         { version { strictly("2.9.2") }}
        api(libs.javaParser)            { version { strictly("3.1.3") }}
        api(libs.jetty)                 { version { strictly(jettyVersion) }}
        api(libs.jettySecurity)         { version { strictly(jettyVersion) }}
        api(libs.jettyWebApp)           { version { strictly(jettyVersion) }}
        api(libs.jsoup)                 { version { strictly("1.11.3") }}
        api(libs.jtar)                  { version { strictly("2.3") }}
        api(libs.kotlinCoroutines)      { version { strictly("1.4.1") }}
        api(libs.kotlinCoroutinesDebug) { version { strictly("1.4.1") }}
        api(libs.littleproxy)           { version { strictly("1.1.3"); because("latest officially released version is incompatible with Guava >= 20") }}
        api(libs.mina)                  { version { strictly("2.0.17") }}
        api(libs.mockitoKotlin)         { version { strictly("1.6.0") }}
        api(libs.mockitoKotlin2)        { version { strictly("2.1.0") }}
        api(libs.mySqlConnector)        { version { strictly("8.0.17") }}
        api(libs.sampleCheck)           { version { strictly("0.12.6") }}
        api(libs.snappy)                { version { strictly("0.4") }}
        api(libs.spock)                 { version { strictly("2.0-M4-groovy-2.5") }}
        api(libs.spockJUnit4)           { version { strictly("2.0-M4-groovy-2.5") }}
        api(libs.sshdCore)              { version { strictly(sshdVersion) }}
        api(libs.sshdScp)               { version { strictly(sshdVersion) }}
        api(libs.sshdSftp)              { version { strictly(sshdVersion) }}
        api(libs.testcontainersSpock)   { version { strictly("1.12.5") }}
        api(libs.xmlunit)               { version { strictly("1.6") }}
    }
}
