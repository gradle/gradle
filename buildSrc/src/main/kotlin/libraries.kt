/**
 * All libraries that are required to compile and run Gradle are listed here.
 * Most of them are also part of the distribution as they are declared as dependencies in one of Gradle"s subprojects.
 * Everything that's in 'Libraries' is added as a dependency constraints with a 'strict' version requirement (see distributions-dependencies.gradle.kts).
 * This way we make sure that a version is not accidentally updated transitively.
 *
 */
object Libraries {
    val all = mutableListOf<Library>()

    val aetherConnector =    lib("org.sonatype.aether:aether-connector-wagon", "1.13.1")
    val ant =                lib("org.apache.ant:ant", "1.9.9")
    val asm =                lib("org.ow2.asm:asm", "6.0")
    val asmCommons =         lib("org.ow2.asm:asm-commons", asm.version)
    val asmTree =            lib("org.ow2.asm:asm-tree", asm.version)
    val asmUtil =            lib("org.ow2.asm:asm-util", asm.version)
    val asmAnalysis =        lib("org.ow2.asm:asm-analysis", asm.version)
    val awsS3Core =          lib("com.amazonaws:aws-java-sdk-core", "1.11.267")
    val awsS3S3 =            lib("com.amazonaws:aws-java-sdk-s3", awsS3Core.version)
    val awsS3Kms =           lib("com.amazonaws:aws-java-sdk-kms", awsS3Core.version)
    val bouncycastleProvider = lib("org.bouncycastle:bcprov-jdk15on", "1.58")
    val bouncycastlePgp =    lib("org.bouncycastle:bcpg-jdk15on", bouncycastleProvider.version)
    val bndlib =             lib("biz.aQute.bnd:biz.aQute.bndlib", "3.4.0")
    val bsh =                lib("org.apache-extras.beanshell:bsh", "2.0b6")
    val commonsCli =         lib("commons-cli:commons-cli", "1.2")
    val commonsCodec =       lib("commons-codec:commons-codec", "1.10")
    val commonsCollections = lib("commons-collections:commons-collections", "3.2.2")
    val commonsCompress =    lib("org.apache.commons:commons-compress", "1.14")
    val commonsHttpclient =  lib("org.apache.httpcomponents:httpclient", "4.5.5")
    val commonsIo =          lib("commons-io:commons-io", "2.4")
    val commonsLang =        lib("commons-lang:commons-lang", "2.6")
    val fastutil =           lib("it.unimi.dsi:fastutil", "7.2.1")
    val gcs =                lib("com.google.apis:google-api-services-storage", "v1-rev116-1.23.0")
    val groovy =             lib("org.codehaus.groovy:groovy-all", "2.4.12")
    val gson =               lib("com.google.code.gson:gson", "2.7")
    val guava =              lib("com.google.guava:guava-jdk5", "17.0")
    val inject =             lib("javax.inject:javax.inject", "1")
    val ivy =                lib("org.apache.ivy:ivy", "2.2.0")
    val jacksonCore =        lib("com.fasterxml.jackson.core:jackson-core", "2.8.11")
    val jacksonAnnotations = lib("com.fasterxml.jackson.core:jackson-annotations", jacksonCore.version)
    val jacksonDatabind =    lib("com.fasterxml.jackson.core:jackson-databind", jacksonCore.version)
    val jansi =              lib("org.fusesource.jansi:jansi", "1.14")
    val jatl =               lib("com.googlecode.jatl:jatl", "0.2.2")
    val jcifs =              lib("org.samba.jcifs:jcifs", "1.3.17")
    val jcip =               lib("net.jcip:jcip-annotations", "1.0")
    val jgit =               lib("org.eclipse.jgit:org.eclipse.jgit", "4.5.3.201708160445-r", "4.6+ requires Java 8")
    val joda =               lib("joda-time:joda-time", "2.8.2")
    val jsch =               lib("com.jcraft:jsch", "0.1.54")
    val jsr305 =             lib("com.google.code.findbugs:jsr305", "2.0.1")
    val junit =              lib("junit:junit", "4.12")
    val junitPlatform =      lib("org.junit.platform:junit-platform-launcher", "1.0.3")
    val kryo =               lib("com.esotericsoftware.kryo:kryo", "2.20")
    val maven3 =             lib("org.apache.maven:maven-core", "3.0.4")
    val maven3WagonFile =    lib("org.apache.maven.wagon:wagon-file", "2.4")
    val maven3WagonHttp =    lib("org.apache.maven.wagon:wagon-http", maven3WagonFile.version)
    val nativePlatform =     lib("net.rubygrapefruit:native-platform", "0.14")
    val nekohtml =           lib("net.sourceforge.nekohtml:nekohtml", "1.9.20")
    val objenesis =          lib("org.objenesis:objenesis", "1.2")
    val plexusContainer =    lib("org.codehaus.plexus:plexus-container-default", "1.5.5")
    val plist =              lib("com.googlecode.plist:dd-plist", "1.20", "for XCode IDE integration support")
    val pmavenCommon =       lib("org.sonatype.pmaven:pmaven-common", "0.8-20100325")
    val pmavenGroovy =       lib("org.sonatype.pmaven:pmaven-groovy", pmavenCommon.version)
    val rhino =              lib("org.mozilla:rhino", "1.7R3")
    val simple =             lib("org.simpleframework:simple", "4.1.21")
    val testng =             lib("org.testng:testng", "6.3.1")
    val xerces =             lib("xerces:xercesImpl", "2.11.0")
    val xmlApis =            lib("xml-apis:xml-apis", "1.4.01", "2.0.x has a POM with relocation Gradle does not handle well")
    val slf4jApi =           lib("org.slf4j:slf4j-api", "1.7.16")
    val jclToSlf4j =         lib("org.slf4j:jcl-over-slf4j", slf4jApi.version)
    val julToSlf4j =         lib("org.slf4j:jul-to-slf4j", slf4jApi.version)
    val log4jToSlf4j =       lib("org.slf4j:log4j-over-slf4j", slf4jApi.version)

    // these are transitive dependencies that are part of the Gradle distribution
    val jetbrainsAnnotations = lib("org.jetbrains:annotations", "13.0")
    val antLauncher = lib("org.apache.ant:ant-launcher", ant.version)
    val minlog = lib("com.esotericsoftware.minlog:minlog", "1.2")
    val aetherApi = lib("org.sonatype.aether:aether-api", aetherConnector.version)
    val aetherImpl = lib("org.sonatype.aether:aether-impl", aetherConnector.version)
    val aetherSpi = lib("org.sonatype.aether:aether-spi", aetherConnector.version)
    val aetherUtil = lib("org.sonatype.aether:aether-util", aetherConnector.version)
    val googleApiClient = lib("com.google.api-client:google-api-client", "1.23.0")
    val googleHttpClient = lib("com.google.http-client:google-http-client", "1.23.0")
    val googleHttpClientJackson2 = lib("com.google.http-client:google-http-client-jackson2", "1.23.0")
    val googleOauthClient = lib("com.google.oauth-client:google-oauth-client", "1.23.0")
    val hamcrest = lib("org.hamcrest:hamcrest-core", "1.3")
    val httpcore = lib("org.apache.httpcomponents:httpcore", "4.4.9")
    val jcommander = lib("com.beust:jcommander", "1.47")
    val maven3AetherProvider = lib("org.apache.maven:maven-aether-provider", maven3.version)
    val maven3Artifact = lib("org.apache.maven:maven-artifact", maven3.version)
    val maven3Compat = lib("org.apache.maven:maven-compat", maven3.version)
    val maven3Model = lib("org.apache.maven:maven-model", maven3.version)
    val maven3ModelBuilder = lib("org.apache.maven:maven-model-builder", maven3.version)
    val maven3PluginApi = lib("org.apache.maven:maven-plugin-api", maven3.version)
    val maven3RepositoryMetadata = lib("org.apache.maven:maven-repository-metadata", maven3.version)
    val maven3Settings = lib("org.apache.maven:maven-settings", maven3.version)
    val maven3SettingsBuilder = lib("org.apache.maven:maven-settings-builder", maven3.version)
    val plexusCipher = lib("org.sonatype.plexus:plexus-cipher", "1.7")
    val plexusClassworlds = lib("org.codehaus.plexus:plexus-classworlds", "2.4")
    val plexusComponentAnnotations = lib("org.codehaus.plexus:plexus-component-annotations", "1.5.5")
    val plexusInterpolation = lib("org.codehaus.plexus:plexus-interpolation", "1.14")
    val plexusSecDispatcher = lib("org.codehaus.plexus:plexus-sec-dispatcher", "1.3")
    val plexusUtils = lib("org.codehaus.plexus:plexus-utils", "3.0.8")
    val snakeyaml = lib("org.yaml:snakeyaml:1.6", "1.6") //added by testng, could be avoided with newer TestNG version
    val maven3WagonHttpShared4 = lib("org.apache.maven.wagon:wagon-http-shared4", maven3WagonFile.version)
    val maven3WagonProviderApi = lib("org.apache.maven.wagon:wagon-provider-api", maven3WagonFile.version)
    val xbeanReflect = lib("org.apache.xbean:xbean-reflect", "3.4")

    private fun lib(coordinates: String, version: String, reason: String? = null) =
        Library(coordinates, version, reason).also { all.add(it) }
}

/**
 * Test classpath libraries
 */
object TestLibraries {
    val spock = "org.spockframework:spock-core:1.0-groovy-2.4"
    val jsoup = "org.jsoup:jsoup:1.6.3"
    val xmlunit = "xmlunit:xmlunit:1.3"
    val jetty = "org.mortbay.jetty:jetty:6.1.26"
    val sshd = "org.apache.sshd:sshd-core:1.2.0"
    val jmock = listOf(
        "org.hamcrest:hamcrest-core",
        "org.hamcrest:hamcrest-library:1.3",
        "org.jmock:jmock:2.5.1",
        "org.jmock:jmock-junit4:2.5.1",
        "org.jmock:jmock-legacy:2.5.1"
    )
}

data class Library(val coordinates: String, val version: String, val reason: String?)
