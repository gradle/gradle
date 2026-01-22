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

description = "Provides a platform dependency to align all distribution versions"

// For the junit-bom
javaPlatform.allowDependencies()

dependencies {
    api(platform(libs.junitBom))

    constraints {
        api(libs.ansiControlSequenceUtil)
        api(libs.ant)
        api(libs.antLauncher)
        api(libs.asm)
        api(libs.asmAnalysis)
        api(libs.asmCommons)
        api(libs.asmTree)
        api(libs.asmUtil)
        api(libs.assertj)
        api(libs.awsS3Core)
        api(libs.awsS3Kms)
        api(libs.awsS3S3)
        api(libs.awsS3Sts)
        api(libs.bouncycastlePgp)
        api(libs.bouncycastlePkix)
        api(libs.bouncycastleProvider)
        api(libs.bouncycastleUtil)
        api(libs.bsh)
        api(libs.commonsCodec)
        api(libs.commonsCompress)
        api(libs.commonsHttpclient)
        api(libs.commonsIo)
        api(libs.commonsLang)
        api(libs.commonsMath)
        api(libs.eclipseSisuPlexus)     { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.errorProneAnnotations)
        api(libs.fastutil)
        api(libs.gradleFileEvents)
        api(libs.gradleProfiler)
        api(libs.develocityTestAnnotation)
        api(libs.gcs)
        api(libs.googleApiClient)       { because("our GCS version requires 1.34.0") }
        api(libs.guava)
        api(libs.googleHttpClientGson)
        api(libs.googleHttpClientApacheV2)
        api(libs.googleHttpClient)      { because("our Google API Client version requires 1.42.2") }
        api(libs.googleOauthClient)     { because("our Google API Client version requires 1.34.1") }
        api(libs.groovy)
        api(libs.groovyAnt)
        api(libs.groovyAstbuilder)
        api(libs.groovyConsole)
        api(libs.groovySql)
        api(libs.groovyDatetime)
        api(libs.groovyDateUtil)
        api(libs.groovyNio)
        api(libs.groovyDoc)
        api(libs.groovyJson)
        api(libs.groovyTemplates)
        api(libs.groovyTest)
        api(libs.groovyXml)
        api(libs.gson)
        api(libs.h2Database)
        api(libs.hamcrest)
        api(libs.hamcrestCore)
        api(libs.hikariCP)              { because("5.x requires Java 11+") }
        api(libs.httpcore)
        api(libs.inject)
        api(libs.ivy)
        api(libs.jacksonAnnotations)
        api(libs.jacksonCore)
        api(libs.jacksonDatabind)
        api(libs.jacksonDatatypeJdk8)
        api(libs.jacksonDatatypeJsr310)
        api(libs.jacksonKotlin)
        api(libs.jakartaActivation)
        api(libs.jakartaXmlBind)
        api(libs.jansi)
        api(libs.jatl)
        api(libs.javaPoet)
        api(libs.jaxbCore)
        api(libs.jaxbImpl)
        api(libs.jcifs)
        api(libs.jclToSlf4j)
        api(libs.jcommander)
        api(libs.jetbrainsAnnotations)
        api(libs.jgit)
        api(libs.jgitSsh)
        api(libs.jgitSshAgent)
        api(libs.joda)
        api(libs.joptSimple)            { because("needed to create profiler in Gradle profiler API") }
        api(libs.jsch)
        api(libs.jsoup)
        api(libs.jsr305)
        api(libs.jspecify)
        api(libs.julToSlf4j)
        api(libs.junit)
        api(libs.junitJupiter)
        api(libs.junit5JupiterApi)
        api(libs.junit5Vintage)
        api(libs.junitPlatform)
        api(libs.junitPlatformEngine)
        api(libs.jzlib)
        api(libs.kryo)
        api(libs.log4jToSlf4j)
        api(libs.maven3Artifact)        { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.maven3Core)            { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.maven3BuilderSupport)  { because("required to load/build poms and repository settings") }
        api(libs.maven3Model)           { because("required to load/build poms and repository settings") }
        api(libs.maven3RepositoryMetadata) { because("required to load/build poms and repository settings") }
        api(libs.maven3Settings)        { because("required to load/build poms and repository settings") }
        api(libs.maven3SettingsBuilder) { because("required to load/build poms and repository settings") }
        api(libs.minlog)
        api(libs.nativePlatform)
        api(libs.objenesis)
        api(libs.plexusCipher)          { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.plexusInterpolation)   { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.plexusClassworlds)     { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.plexusSecDispatcher)   { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.plexusUtils)           { because("transitive dependency of Maven modules to process POM metadata") }
        api(libs.plist)
        api(libs.servletApi)
        api(libs.slf4jApi)
        api(libs.slf4jSimple)           { because("We only need the logging API, we supply our own binding, which cause duplicate binding on class path error") }
        api(libs.snakeyaml)
        api(libs.testng)                { because("later versions break test cross-version test filtering") }
        api(libs.tomlj)
        api(libs.trove4j)
        api(libs.jna)
        api(libs.jnaPlatform)
        api(libs.xbeanReflect)
        api(libs.jnrConstants)
        api(libs.xmlApis)               { because("2.0.x has a POM with relocation Gradle does not handle well") }

        // compile only
        api(libs.maven3Compat)          { because("required for maven2gradle in init plugin") }
        api(libs.maven3PluginApi)       { because("required for maven2gradle in init plugin") }
        api(libs.zinc)

        // test only
        api(libs.aircompressor)
        api(libs.archunit)
        api(libs.archunitJunit5)
        api(libs.archunitJunit5Api)
        api(libs.awaitility)
        api(libs.bytebuddy)
        api(libs.bytebuddyAgent)
        api(libs.cglib)
        api(libs.compileTesting)
        api(libs.dockerJavaApi)
        api(libs.equalsverifier)
        api(libs.guice)
        api(libs.httpmime)
        api(libs.javaParser)
        api(libs.jetty)
        api(libs.jettySecurity)
        api(libs.jettyServer)
        api(libs.jettyServlet)
        api(libs.jettyUtil)
        api(libs.jettyWebApp)
        api(libs.jtar)
        api(libs.kotlinJvmAbiGenEmbeddable)
        api(libs.kotlinxSerializationCore)
        api(libs.kotlinxSerializationJson)
        api(libs.kotlinxCoroutinesJvm)
        api(libs.littleproxy)
        api(libs.maven3ResolverProvider)
        api(libs.mavenResolverApi)
        api(libs.mavenResolverConnectorBasic)
        api(libs.mavenResolverImpl)
        api(libs.mavenResolverSupplier)
        api(libs.mavenResolverTransportFile)
        api(libs.mavenResolverTransportHttp)
        api(libs.mockitoCore)
        api(libs.mockitoKotlin)
        api(libs.mockwebserver)
        api(libs.mySqlConnector)
        api(libs.netty)
        api(libs.opentest4j)
        api(libs.samplesCheck)
        api(libs.samplesDiscovery)
        api(libs.snappy)
        api(libs.socksProxy)
        api(libs.spock)
        api(libs.spockJUnit4)
        api(libs.sshdCore)
        api(libs.sshdOsgi)              { because("It contains sshd-core and sshd-common classes") }
        api(libs.sshdScp)
        api(libs.sshdSftp)
        api(libs.testcontainers)
        api(libs.testcontainersSpock)
        api(libs.xerces)
        api(libs.xmlunit)
    }
}

