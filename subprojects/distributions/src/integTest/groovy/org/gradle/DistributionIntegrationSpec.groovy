/*
 * Copyright 2010 the original author or authors.
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

package org.gradle

import com.google.common.collect.ImmutableMap
import org.apache.commons.io.IOUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GUtil
import org.gradle.util.GradleVersion
import org.gradle.util.PreconditionVerifier
import org.junit.Rule
import spock.lang.Shared

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat

abstract class DistributionIntegrationSpec extends AbstractIntegrationSpec {

    protected static final THIRD_PARTY_LIBS = collectThirdPartyLibs()

    @Rule public final PreconditionVerifier preconditionVerifier = new PreconditionVerifier()

    @Shared String baseVersion = GradleVersion.current().baseVersion.version

    abstract String getDistributionLabel()

    /**
     * Change this whenever you add or remove a new subproject.
     */
    int getCoreLibJarsCount() {
        32
    }

    /**
     * Change this if you added or removed dependencies.
     */
    int getThirdPartyLibJarsCount() {
        THIRD_PARTY_LIBS.size() + 54
    }

    int getLibJarsCount() {
        coreLibJarsCount + thirdPartyLibJarsCount
    }

    def "no duplicate entries"() {
        given:
        def entriesByPath = zipEntries.findAll { !it.name.contains('/META-INF/services/') }.groupBy { it.name }
        def dupes = entriesByPath.findAll { it.value.size() > 1 }

        when:
        def dupesWithCount = dupes.collectEntries { [it.key, it.value.size()]}

        then:
        dupesWithCount.isEmpty()
    }

    def "all files under lib directory are jars"() {
        when:
        def nonJarLibEntries = libZipEntries.findAll { !it.name.endsWith(".jar") }

        then:
        nonJarLibEntries.isEmpty()
    }

    def "no additional jars are added to the distribution"() {
        when:
        def jarLibEntries = libZipEntries.findAll { it.name.endsWith(".jar") }

        then:
        //ME: This is not a foolproof way of checking that additional jars have not been accidentally added to the distribution
        //but should be good enough. If this test fails for you and you did not intend to add new jars to the distribution
        //then there is something to be fixed. If you intentionally added new jars to the distribution and this is now failing please
        //accept my sincere apologies that you have to manually bump the numbers here.
        jarLibEntries.size() == libJarsCount
    }

    protected List<? extends ZipEntry> getLibZipEntries() {
        zipEntries.findAll { !it.isDirectory() && it.name.tokenize("/")[1] == "lib" }
    }

    protected List<? extends ZipEntry> getZipEntries() {
        ZipFile zipFile = new ZipFile(zip)
        try {
            zipFile.entries().toList()
        } finally {
            zipFile.close()
        }
    }

    protected TestFile unpackDistribution(type = getDistributionLabel(), TestFile into = testDirectory) {
        TestFile zip = getZip(type)
        zip.usingNativeTools().unzipTo(into)
        assert into.listFiles().size() == 1
        into.listFiles()[0]
    }

    protected TestFile getZip(String type = getDistributionLabel()) {
        buildContext.distributionsDir.file("gradle-$baseVersion-${type}.zip")
    }

    protected void checkMinimalContents(TestFile contentsDir) {
        // Check it can be executed
        executer.inDirectory(contentsDir).usingExecutable('bin/gradle').withTaskList().run()

        // Scripts
        contentsDir.file('bin/gradle').assertIsFile()
        contentsDir.file('bin/gradle.bat').assertIsFile()

        // Top level files
        contentsDir.file('LICENSE').assertIsFile()

        // Core libs
        def coreLibs = contentsDir.file("lib").listFiles().findAll {
            it.name.startsWith("gradle-") && !it.name.startsWith("gradle-api-metadata") && !it.name.startsWith("gradle-kotlin-dsl")
        }
        assert coreLibs.size() == coreLibJarsCount
        coreLibs.each { assertIsGradleJar(it) }

        def toolingApiJar = contentsDir.file("lib/gradle-tooling-api-${baseVersion}.jar")
        toolingApiJar.assertIsFile()
        assert toolingApiJar.length() < 378 * 1024 // tooling api jar is the small plain tooling api jar version and not the fat jar.

        // Plugins
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-dependency-management-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-version-control-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-plugins-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-scala-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-code-quality-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-antlr-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-maven-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-signing-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ear-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-jvm-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-jvm-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-java-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-groovy-${baseVersion}.jar"))

        // Docs
        contentsDir.file('README').assertIsFile()

        // Others
        assertIsGradleApiMetadataJar(contentsDir.file("lib/gradle-api-metadata-${baseVersion}.jar"))

        // Jars that must not be shipped
        assert !contentsDir.file("lib/tools.jar").exists()
        assert !contentsDir.file("lib/plugins/tools.jar").exists()
    }

    protected void assertDocsExist(TestFile contentsDir, String version) {
        // Javadoc
        contentsDir.file('docs/javadoc/index.html').assertIsFile()
        contentsDir.file('docs/javadoc/index.html').assertContents(containsString("Gradle API ${version}"))
        contentsDir.file('docs/javadoc/org/gradle/api/Project.html').assertIsFile()

        // Userguide
        contentsDir.file('docs/userguide/userguide.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide.html').assertContents(containsString("Gradle User Manual</h1>"))
        contentsDir.file('docs/userguide/userguide_single.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide_single.html').assertContents(containsString("<h1>Gradle User Manual: Version ${version}</h1>"))
        contentsDir.file('docs/userguide/userguide.pdf').assertIsFile()

        // DSL reference
        contentsDir.file('docs/dsl/index.html').assertIsFile()
        contentsDir.file('docs/dsl/index.html').assertContents(containsString("<title>Gradle DSL Version ${version}</title>"))
    }

    protected void assertIsGradleJar(TestFile jar) {
        jar.assertIsFile()
        assertThat(jar.manifest.mainAttributes.getValue('Implementation-Version'), equalTo(baseVersion))
        assertThat(jar.manifest.mainAttributes.getValue('Implementation-Title'), equalTo('Gradle'))
    }

    private static void assertIsGradleApiMetadataJar(TestFile jar) {
        new JarTestFixture(jar.canonicalFile).with {
            def apiDeclaration = GUtil.loadProperties(IOUtils.toInputStream(content("gradle-api-declaration.properties")))
            assert apiDeclaration.size() == 2
            assert apiDeclaration.getProperty("includes").contains(":org/gradle/api/**:")
            assert apiDeclaration.getProperty("excludes").split(":").size() == 1
        }
    }

    private static Map<String, String> collectThirdPartyLibs() {
        def reducedNativePlatformSubset = false
        def nativePlatformVersion = "0.21"

        def builder = ImmutableMap.<String, String>builder()
        addLibrary(builder, "annotations-13.0.jar", "ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478")
        addLibrary(builder, "ant-1.10.7.jar", "dab4d3b2e45b73aec95cb25ce5050a651ad060f50f74662bbc3c1cb406ec1d19")
        addLibrary(builder, "ant-launcher-1.10.7.jar", "749d131ab53fd292041245bf24e4b6a6241766f17a5b987a4e4d833cd4234ae6")
        addLibrary(builder, "asm-7.1.jar", "4ab2fa2b6d2cc9ccb1eaa05ea329c407b47b13ed2915f62f8c4b8cc96258d4de")
        addLibrary(builder, "asm-analysis-7.1.jar", "4612c0511a63db2a2570f07ad1959e19ed8eb703e4114da945cb85682519a55c")
        addLibrary(builder, "asm-commons-7.1.jar", "e5590489d8f1984d85bfeabd3b17374c59c28ae09d48ec4a0ebbd01959ecd358")
        addLibrary(builder, "asm-tree-7.1.jar", "c0e82b220b0a52c71c7ca2a58c99a2530696c7b58b173052b9d48fe3efb10073")
        addLibrary(builder, "commons-compress-1.19.jar", "ff2d59fad74e867630fbc7daab14c432654712ac624dbee468d220677b124dd5")
        addLibrary(builder, "commons-io-2.6.jar", "f877d304660ac2a142f3865badfc971dec7ed73c747c7f8d5d2f5139ca736513")
        addLibrary(builder, "commons-lang-2.6.jar", "50f11b09f877c294d56f24463f47d28f929cf5044f648661c0f0cfbae9a2f49c")
        addLibrary(builder, "failureaccess-1.0.1.jar", "a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26")
        addLibrary(builder, "groovy-all-1.3-2.5.8.jar", "25394ae305024c27bca108eeb7e9895e97c6dc1e9701f341502bf10dd00bef33")
        addLibrary(builder, "guava-27.1-android.jar", "686404f2d1d4d221911f96bd627ff60dac2226a5dfa6fb8ba517073eb97ec0ef")
        addLibrary(builder, "jansi-1.18.jar", "109e64fc65767c7a1a3bd654709d76f107b0a3b39db32cbf11139e13a6f5229b")
        addLibrary(builder, "javax.inject-1.jar", "91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff")
        addLibrary(builder, "jcl-over-slf4j-1.7.28.jar", "b81f5f910da9708c7a6a77b720a7de20154cced4065b56f33301945c04aaad70")
        addLibrary(builder, "jsr305-3.0.2.jar", "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7")
        addLibrary(builder, "jul-to-slf4j-1.7.28.jar", "67c99ffdef691c3b0f817e130c2047fa43ecf12017613ff597f66f768d745475")
        addLibrary(builder, "kotlin-daemon-embeddable-1.3.61.jar", "81ac228adb7c4d39bc4640ece66573ec6f752cbfa13d9b6959e010de55df97af")
        addLibrary(builder, "kotlin-reflect-1.3.61.jar", "143e715c10ff6d65eb5a7695be7b696c6e013702dff103d23ba54760bf93867b")
        addLibrary(builder, "kotlin-sam-with-receiver-compiler-plugin-1.3.61.jar", "2a5d7cc9db607562919f50792f9a39658ec1c9db13f600854ab750edbbfd9da7")
        addLibrary(builder, "kotlin-scripting-common-1.3.61.jar", "3041dbb12b63e46c9f4b43db012381b89109e313208b583ce66a1e622294c1ec")
        addLibrary(builder, "kotlin-scripting-jvm-1.3.61.jar", "b76fddd6d9a7cfb5de9037048677520baec0547210b82d3c3f26d170396414b8")
        addLibrary(builder, "kotlin-scripting-jvm-host-embeddable-1.3.61.jar", "000b7aeec82f8e4bc87bdfe53ec9b6cfe9b92a99a03d16918ac911a26c2f4e20")
        addLibrary(builder, "kotlin-script-runtime-1.3.61.jar", "14ac9a3db7e85983f51f80e5ba027f21575ce2241be1c05f029dd836ac38ec48")
        addLibrary(builder, "kotlin-scripting-compiler-embeddable-1.3.61.jar", "190d1bb496a56642bdb49f07f48bc64c67d6e71c951124b8a88d4f69d5edd50b")
        addLibrary(builder, "kotlin-scripting-compiler-impl-embeddable-1.3.61.jar", "3885815dfaad81221a55b5c5332664ab596fd76159c6e1b4c85d866af0048255")
        addLibrary(builder, "kotlin-stdlib-1.3.61.jar", "e51e512619a7e7650a30eb4eb3e9c03e6909c7b5e3c026404e076254c098b932")
        addLibrary(builder, "kotlin-stdlib-common-1.3.61.jar", "a2e7f341cf3047b5f00a1917ef777d323cdab2a57377468b8ed62aa31469cf7f")
        addLibrary(builder, "kotlin-stdlib-jdk7-1.3.61.jar", "11f4a57e3e7d81f3f152d5dcefe39bd77614b5a94125ff3b11526b0a19ac3989")
        addLibrary(builder, "kotlin-stdlib-jdk8-1.3.61.jar", "3839ba7deb798375da1807bc469d1cf315db7a6275599f733184374772ec3b21")
        addLibrary(builder, "kotlinx-metadata-jvm-0.1.0.jar", "9753bb39efef35957c5c15df9a3cb769aabf2cdfa74b47afcb7760e5146be3b5")
        addLibrary(builder, "kryo-2.24.0.jar", "7e56b32c635058f9aa2820f88919ab702d029cbcd15285da9992e36cc0ae52f2")
        addLibrary(builder, "log4j-over-slf4j-1.7.28.jar", "c24e45c905f0c3b1dcc873164f5409bbfe3ee8860e366d1cd2190f798227f864")
        addLibrary(builder, "minlog-1.2.jar", "a678cb1aa8f5d03d901c992c75741841d98a9bc3d55dad02e84d65315c4e60f2")

        addLibrary(builder, "native-platform-${nativePlatformVersion}.jar", "1ab74b54d2785ea53db3b6ebf9ce4ab19ce34a623c684de157c7c3ffa2c780d5")
        addLibrary(builder, "native-platform-linux-amd64-${nativePlatformVersion}.jar", "9aabe18ecfc3cc37a245ec2ddf6d2a7a91108b49e217f3202c98b6dd2d9c15e1")
        addLibrary(builder, "native-platform-linux-amd64-ncurses5-${nativePlatformVersion}.jar", "d8c2e348401301fd4c5b67035dd53ee04aa52cb2dfd78406701923323b0817a2")
        addLibrary(builder, "native-platform-osx-amd64-${nativePlatformVersion}.jar", "2129b05584927e4598f2a15bb0e14581c87471325964f9e2f39fac66a5cba4a2")
        addLibrary(builder, "native-platform-windows-amd64-${nativePlatformVersion}.jar", "01479225c23c7ef6b8ac1362bebd8010e7fe838d8a41b5bf18e1a48d89d370c6")
        addLibrary(builder, "native-platform-windows-amd64-min-${nativePlatformVersion}.jar", "1740fd53af318fcc3e8abf441e60f4ef246c7f271efb41f97f6f84de1852cfb1")
        addLibrary(builder, "native-platform-windows-i386-${nativePlatformVersion}.jar", "6a536de8acffc236c186bad0ffcf9eeceaa9b2b8ee7b2b66797ebe1fc33f1c6b")
        addLibrary(builder, "native-platform-windows-i386-min-${nativePlatformVersion}.jar", "622ab8e07daaab0cc1e9f06515a9aa89cb87fff4f9a6be00f81edd2d4c0184a0")

        if (!reducedNativePlatformSubset) {
            addLibrary(builder, "native-platform-freebsd-amd64-libcpp-${nativePlatformVersion}.jar", "4006623362fb99c53915645aeebc09fa134a58038d9c3b150f852230f2c3cb8c")
            addLibrary(builder, "native-platform-freebsd-amd64-libstdcpp-${nativePlatformVersion}.jar", "aa4e8ad8377b7c676b75069ed52e34aab918a0edcd17b4e23f1909cfe57b2736")
            addLibrary(builder, "native-platform-freebsd-i386-libcpp-${nativePlatformVersion}.jar", "6dd1272b089720399146e51090bd2858407f45e46297ff0b09140c52934189e1")
            addLibrary(builder, "native-platform-freebsd-i386-libstdcpp-${nativePlatformVersion}.jar", "cc87393e249faa037647c2c4155b1b2a2621e81a7e33df90ce56cc6b76b9f8e4")
            addLibrary(builder, "native-platform-linux-amd64-ncurses6-${nativePlatformVersion}.jar", "d72ab295015517e009dca8ed7585d3569c530a685b75798acf962b735ba8fc50")
            addLibrary(builder, "native-platform-linux-i386-${nativePlatformVersion}.jar", "f367bd39fb666231d0ed0cb673dd989a587d816646c1ff91fbd97eacd0106a6e")
            addLibrary(builder, "native-platform-linux-i386-ncurses5-${nativePlatformVersion}.jar", "5541e3ef3a0f0b55b47a88057d4d4436c596ad6160b3b561f74438326ce7c98d")
            addLibrary(builder, "native-platform-linux-i386-ncurses6-${nativePlatformVersion}.jar", "6923b64737529cd1f02299773c0b1d33b515d277bed902ee97a8dea60f834303")
            addLibrary(builder, "native-platform-linux-aarch64-${nativePlatformVersion}.jar", "fbafc169adc7766c3e94974ab38d853114a90d9a5494457c09a3f6cbbc5d08f7")
            addLibrary(builder, "native-platform-linux-aarch64-ncurses5-${nativePlatformVersion}.jar", "ad41d9726523d4f317f6524c1be767295ff137f21f79f87150386965ffdd52ee")
            addLibrary(builder, "native-platform-linux-aarch64-ncurses6-${nativePlatformVersion}.jar", "c2f9d6f7d537921cbb075b2b2c0463f23aa1ca7a07a8c81285bec04442742c74")
        }

        addLibrary(builder, "objenesis-2.6.jar", "5e168368fbc250af3c79aa5fef0c3467a2d64e5a7bd74005f25d8399aeb0708d")
        addLibrary(builder, "plugins/aether-api-1.13.1.jar", "ae8dc80232771f8913febfa410c5719e9ba8ded81fb99788e214fd676dbbe13f")
        addLibrary(builder, "plugins/aether-connector-wagon-1.13.1.jar", "df54e8505104228ee7e3fbdead7a7a9cb753b04ca6c9cf60a6b19aee0737f1ec")
        addLibrary(builder, "plugins/aether-impl-1.13.1.jar", "865511994805827e88f327944a089142bb7f3d88cde271ba3dceb732cb137a93")
        addLibrary(builder, "plugins/aether-spi-1.13.1.jar", "d5de4e299be5a79feb1dbe8ff3814034c6e44314b4c00b92ffa8d97576ded5b3")
        addLibrary(builder, "plugins/aether-util-1.13.1.jar", "687799a0ce988bee9e8eb9ae0ba870300adc0114248ad4a4327bdb625d27e010")
        addLibrary(builder, "plugins/apiguardian-api-1.0.0.jar", "1f58b77470d8d147a0538d515347dd322f49a83b9e884b8970051160464b65b3")
        addLibrary(builder, "plugins/asm-util-7.1.jar", "a24485517596ae1003dcf2329c044a2a861e5c25d4476a695ccaacf560c74d1a")
        addLibrary(builder, "plugins/aws-java-sdk-core-1.11.633.jar", "aa4199cd1b52484f9535c36f6ef80f104369aa070912397eeb5c25a41dd1f83e")
        addLibrary(builder, "plugins/aws-java-sdk-kms-1.11.633.jar", "6a71923945e91f554899481f46fbed66991de8bb9d4ad375c0b8ac802ef28b10")
        addLibrary(builder, "plugins/aws-java-sdk-s3-1.11.633.jar", "d4db7809743ee5155da1b9899919369824df6e8ee04db793bd0d73b40b81c2ec")
        addLibrary(builder, "plugins/bcpg-jdk15on-1.63.jar", "dc4f51adfc46583c2543489c82708fef5660202bf264c7cd453f081a117ea536")
        addLibrary(builder, "plugins/bcprov-jdk15on-1.63.jar", "28155c8695934f666fabc235f992096e40d97ecb044d5b6b0902db6e15a0b72f")
        addLibrary(builder, "plugins/bcpkix-jdk15on-1.61.jar", "326eb81c2a0cb0d665733a9cc7c03988081101ad17d1453b334368453658591f")
        addLibrary(builder, "plugins/commons-codec-1.13.jar", "61f7a3079e92b9fdd605238d0295af5fd11ac411a0a0af48deace1f6c5ffa072")
        addLibrary(builder, "plugins/bsh-2.0b6.jar", "a17955976070c0573235ee662f2794a78082758b61accffce8d3f8aedcd91047")
        addLibrary(builder, "plugins/dd-plist-1.21.jar", "019c61abd93ecf614e3d214e9fed942dcf47d9d2d9548fe59d70f0840ba32fb6")
        addLibrary(builder, "plugins/google-api-client-1.25.0.jar", "24e1a69d6c04e6e72e3e16757d46d32daa7dd43cb32c3895f832f25358be1402")
        addLibrary(builder, "plugins/google-api-services-storage-v1-rev136-1.25.0.jar", "c517a94e5ff2670470fc8c3ae690e7d0d28d210276e6f2228aaafead994c59d1")
        addLibrary(builder, "plugins/google-http-client-1.25.0.jar", "fb7d80a515da4618e2b402e1fef96999e07621b381a5889ef091482c5a3e961d")
        addLibrary(builder, "plugins/google-http-client-jackson2-1.25.0.jar", "f9e7e0d318860a2092d70b56331976280c4e9348a065ede3b99c92aa032fd853")
        addLibrary(builder, "plugins/google-oauth-client-1.25.0.jar", "7e2929133d4231e702b5956a7e5dc8347a352acc1e97082b40c3585b81cd3501")
        addLibrary(builder, "plugins/gson-2.8.5.jar", "233a0149fc365c9f6edbd683cfe266b19bdc773be98eabdaf6b3c924b48e7d81")
        addLibrary(builder, "plugins/hamcrest-core-1.3.jar", "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9")
        addLibrary(builder, "plugins/httpclient-4.5.10.jar", "38b9f16f504928e4db736a433b9cd10968d9ec8d6f5d0e61a64889a689172134")
        addLibrary(builder, "plugins/httpcore-4.4.12.jar", "ab765334beabf0ea024484a5e90a7c40e8160b145f22d199e11e27f68d57da08")
        addLibrary(builder, "plugins/ion-java-1.0.2.jar", "0d127b205a1fce0abc2a3757a041748651bc66c15cf4c059bac5833b27d471a5")
        addLibrary(builder, "plugins/ivy-2.3.0.jar", "ff3543305c62f23d1a4cafc66fab9c9f55ea169ccf2b6c040d3fa23254b86b18")
        addLibrary(builder, "plugins/jackson-annotations-2.9.10.jar", "c876f2e85d0f108a34cdd11ccc9d8d7875697367efc75bf10a89c2c26aee994c")
        addLibrary(builder, "plugins/jackson-core-2.9.10.jar", "65fe26d7554a4409652c86ee38f2e94bc42934326d88b3c78c61f66ff2222c53")
        addLibrary(builder, "plugins/jackson-databind-2.9.10.jar", "49bb71a73fcdcdf59c40a1a01d7245f41d3a8ba96ea6182b720f0c6167241757")
        addLibrary(builder, "plugins/jatl-0.2.3.jar", "93ee3810f7275244f5f6311d50c587d8fe43ab5047dfa3291aa96fe50587501c")
        addLibrary(builder, "plugins/jaxb-impl-2.3.2.jar", "b3a3d3aeaa5503cadc24dca9539b87efcd7bc3bd8c48018887a1489f2a77bdc0")
        addLibrary(builder, "plugins/jcifs-1.3.17.jar", "3b077938f676934bc20bde821d1bdea2cd6d55d96151218b40fd159532f45061")
        addLibrary(builder, "plugins/jcommander-1.72.jar", "e0de160b129b2414087e01fe845609cd55caec6820cfd4d0c90fabcc7bdb8c1e")
        addLibrary(builder, "plugins/jmespath-java-1.11.633.jar", "6ca34bef5587d6f3986199df310fd5b54f139c700f4bf14ed8c258ad3e1d0e77")
        addLibrary(builder, "plugins/joda-time-2.10.4.jar", "ac6fda8989775776f428df8b5a4517cdb06d923465abf9bda0746ec07dfcc657")
        addLibrary(builder, "plugins/jsch-0.1.55.jar", "d492b15a6d2ea3f1cc39c422c953c40c12289073dbe8360d98c0f6f9ec74fc44")
        addLibrary(builder, "plugins/junit-4.12.jar", "59721f0805e223d84b90677887d9ff567dc534d7c502ca903c0c2b17f05c116a")
        addLibrary(builder, "plugins/junit-platform-commons-1.3.1.jar", "457d8e1c0c80d1e320a792ec35e7c180694ba05182d7ecf7dabdb4e5a8a12fe2")
        addLibrary(builder, "plugins/junit-platform-engine-1.3.1.jar", "303d0546c3e950cc3beaca00dfcbf2632d4eca530e4e446391bf193cbc2a71a3")
        addLibrary(builder, "plugins/junit-platform-launcher-1.3.1.jar", "6f698076c33cf27d2b80de11506031b625733aab7f18e2d4c5d15803f27231dd")
        addLibrary(builder, "plugins/jzlib-1.1.3.jar", "89b1360f407381bf61fde411019d8cbd009ebb10cff715f3669017a031027560")
        addLibrary(builder, "plugins/maven-aether-provider-3.0.4.jar", "33ff4aabbd0d02e4dd8279cda8f366c69915302bc4bb97bc01814a985f5c0643")
        addLibrary(builder, "plugins/maven-artifact-3.0.4.jar", "3c199a96af9550872724f41c053d7839dfcc6512e7704fa16c675363c4146796")
        addLibrary(builder, "plugins/maven-compat-3.0.4.jar", "a2d02378d413d9e87833780820163902c5be4b0f3c44b65de7608f47cd94599f")
        addLibrary(builder, "plugins/maven-core-3.0.4.jar", "3dd795c0ad9742a0be65a2a5ec22428d59dd2a891a7565ae94f64661e3740528")
        addLibrary(builder, "plugins/maven-model-3.0.4.jar", "26b6825ea73ac4d7b1a6f5e62ac1c11b0fc272504da6dde9ba8f894cd847e1c1")
        addLibrary(builder, "plugins/maven-model-builder-3.0.4.jar", "b4f1d3ae53c290e1ae45694c5ce2d17bf8d577ff5ece0f9aa0cffe151a6ef4e7")
        addLibrary(builder, "plugins/maven-plugin-api-3.0.4.jar", "4e5ee7f7ab7e43f691788489e59f2da4a322e3e35f2a2d8b714ad929f624eead")
        addLibrary(builder, "plugins/maven-repository-metadata-3.0.4.jar", "a25c4db27cffda9e9229db168b1190d6a3e5439f3f67d6afec3df9470e0752d5")
        addLibrary(builder, "plugins/maven-settings-3.0.4.jar", "3e3df17f5df5e4ce1e7b7f2011c57d61d328e65678542ade2048f0d0fa295f09")
        addLibrary(builder, "plugins/maven-settings-builder-3.0.4.jar", "a38a54ec1e69a30ddfc14434e0aec2764fc268668abcc0e132d86692a5dce3e4")
        addLibrary(builder, "plugins/nekohtml-1.9.22.jar", "452978e8b6667c7b8357fd3f0a2f2f405e4560a7148143a69181735da5d19045")
        addLibrary(builder, "plugins/opentest4j-1.1.1.jar", "f106351abd941110226745ed103c85863b3f04e9fa82ddea1084639ae0c5336c")
        addLibrary(builder, "plugins/org.eclipse.jgit-5.5.0.201909110433-r.jar", "d5b2cd6284744abbc63ccc10f4c2039a6bc010e9d697c26999f30c4705e5fdcf")
        addLibrary(builder, "plugins/plexus-cipher-1.7.jar", "114859861ff10f987b880d6f34e3215274af3cc92b3a73831c84d596e37c6511")
        addLibrary(builder, "plugins/plexus-classworlds-2.5.1.jar", "de9ce33b29088c2db7c3f55ddc061c2a7a72f9c93c28faad62cc15aee26b6888")
        addLibrary(builder, "plugins/plexus-component-annotations-1.5.5.jar", "4df7a6a7be64b35bbccf60b5c115697f9ea3421d22674ae67135dde375fcca1f")
        addLibrary(builder, "plugins/plexus-container-default-1.7.1.jar", "f3f61952d63675ef61b42fa4256c1635749a5bc17668b4529fccde0a29d8ee19")
        addLibrary(builder, "plugins/plexus-interpolation-1.14.jar", "7fc63378d3e84663619b9bedace9f9fe78b276c2be3c62ca2245449294c84176")
        addLibrary(builder, "plugins/plexus-sec-dispatcher-1.3.jar", "3b0559bb8432f28937efe6ca193ef54a8506d0075d73fd7406b9b116c6a11063")
        addLibrary(builder, "plugins/plexus-utils-3.1.0.jar", "0ffa0ad084ebff5712540a7b7ea0abda487c53d3a18f78c98d1a3675dab9bf61")
        addLibrary(builder, "plugins/pmaven-common-0.8-20100325.jar", "129306abcdb337cd53e710aadafaa226f017997e6bc81c2cb50deb00916ca8d8")
        addLibrary(builder, "plugins/pmaven-groovy-0.8-20100325.jar", "87891a373d079fd370e859b1f5bd0579d8b4a68e515fa8bbae91301c28f48d57")
        addLibrary(builder, "plugins/rhino-1.7.10.jar", "38eb3000cf56b8c7559ee558866a768eebcbf254174522d6404b7f078f75c2d4")
        addLibrary(builder, "plugins/simple-4.1.21.jar", "8e6439fcc1f2fe87b5dfc20eb13289acba8047e9cfadf45ad3dc5c57a9bcd054")
        addLibrary(builder, "plugins/snakeyaml-1.17.jar", "5666b36f9db46f06dd5a19d73bbff3b588d5969c0f4b8848fde0f5ec849430a5")
        addLibrary(builder, "plugins/testng-6.3.1.jar", "57ed8e83e357c3838daad81b789a2753227fee8cfb86aa31a61b765c4093d6ad")
        addLibrary(builder, "plugins/wagon-file-3.0.0.jar", "63324036899559f94154e6f845e62453a1f202c1b21b80060f2d88a05a05a272")
        addLibrary(builder, "plugins/wagon-http-3.0.0.jar", "9f68fed6684c2245a62d5d3c8b4954b882562797e96b15ce0f18514c543fb999")
        addLibrary(builder, "plugins/wagon-http-shared-3.0.0.jar", "40c51265b3ed90b6919c08dcdf3620dc614894ef60bacdf4c34bdbe295b44c49")
        addLibrary(builder, "plugins/wagon-provider-api-3.0.0.jar", "04de4d2f39178998ef3ce5f6d91a358363ad3f5270e897d5547321ea69fa2992")
        addLibrary(builder, "plugins/xbean-reflect-3.7.jar", "104e5e9bb5a669f86722f32281960700f7ec8e3209ef51b23eb9b6d23d1629cb")
        addLibrary(builder, "plugins/xercesImpl-2.12.0.jar", "b50d3a4ca502faa4d1c838acb8aa9480446953421f7327e338c5dda3da5e76d0")
        addLibrary(builder, "slf4j-api-1.7.28.jar", "fb6e4f67a2a4689e3e713584db17a5d1090c1ebe6eec30e9e0349a6ee118141e")
        addLibrary(builder, "trove4j-1.0.20181211.jar", "affb7c85a3c87bdcf69ff1dbb84de11f63dc931293934bc08cd7ab18de083601")
        addLibrary(builder, "xml-apis-1.4.01.jar", "a840968176645684bb01aed376e067ab39614885f9eee44abe35a5f20ebe7fad")

        return builder.build()
    }

    private static void addLibrary(ImmutableMap.Builder<String, String> builder, String library, String sha1) {
        builder.put(library, sha1)
    }
}
