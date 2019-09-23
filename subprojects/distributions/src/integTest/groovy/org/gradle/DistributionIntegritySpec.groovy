/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import java.util.zip.ZipFile

class DistributionIntegritySpec extends DistributionIntegrationSpec {

    /*
     * Integration test to verify the integrity of the dependencies. The goal is to be able to check the dependencies
     * even we assume that the Gradle binaries are compromised. Ultimately this test should run outside of the Gradle.
     */

    @Override
    String getDistributionLabel() {
        'bin'
    }

    def "verify 3rd-party dependencies jar hashes"() {
        setup:
        // dependencies produced by Gradle and cannot be verified by this test
        def excluded = ['gradle-', 'fastutil-8.2.1-min', 'kotlin-compiler-embeddable-1.3.50-patched']

        def expectedHashes = [
            'annotations-13.0.jar' : 'ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478',
            'ant-1.9.14.jar' : '37c10bafb295aef7a8618da2ff82f0dd2d0cbe2bbbb7d5ea994ea615238bd041',
            'ant-launcher-1.9.14.jar' : 'faaf02c32f0649a2ebd146166b96a5c80cfddd3934fa0c260267e9c5be96088d',
            'asm-7.1.jar' : '4ab2fa2b6d2cc9ccb1eaa05ea329c407b47b13ed2915f62f8c4b8cc96258d4de',
            'asm-analysis-7.1.jar' : '4612c0511a63db2a2570f07ad1959e19ed8eb703e4114da945cb85682519a55c',
            'asm-commons-7.1.jar' : 'e5590489d8f1984d85bfeabd3b17374c59c28ae09d48ec4a0ebbd01959ecd358',
            'asm-tree-7.1.jar' : 'c0e82b220b0a52c71c7ca2a58c99a2530696c7b58b173052b9d48fe3efb10073',
            'commons-compress-1.18.jar' : '5f2df1e467825e4cac5996d44890c4201c000b43c0b23cffc0782d28a0beb9b0',
            'commons-io-2.6.jar' : 'f877d304660ac2a142f3865badfc971dec7ed73c747c7f8d5d2f5139ca736513',
            'commons-lang-2.6.jar' : '50f11b09f877c294d56f24463f47d28f929cf5044f648661c0f0cfbae9a2f49c',
            'failureaccess-1.0.1.jar' : 'a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26',
            'groovy-all-1.3-2.5.8.jar': '25394ae305024c27bca108eeb7e9895e97c6dc1e9701f341502bf10dd00bef33',
            'guava-27.1-android.jar' : '686404f2d1d4d221911f96bd627ff60dac2226a5dfa6fb8ba517073eb97ec0ef',
            'jansi-1.17.1.jar' : 'b2234bfb0d8f245562d64ed9325df6b907093f4daa702c9082d4796db2a2d894',
            'javax.inject-1.jar' : '91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff',
            'jcl-over-slf4j-1.7.25.jar' : '5e938457e79efcbfb3ab64bc29c43ec6c3b95fffcda3c155f4a86cc320c11e14',
            'jsr305-3.0.2.jar' : '766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7',
            'jul-to-slf4j-1.7.25.jar' : '416c5a0c145ad19526e108d44b6bf77b75412d47982cce6ce8d43abdbdbb0fac',
            'kotlin-daemon-embeddable-1.3.50.jar': 'c7b1b872e04a6fdfcfb1d0451de8a49f9900608bae93f9120c35bbcde2d911fa',
            'kotlin-reflect-1.3.50.jar': '64583199ea5a54aefd1bd1595288925f784226ee562d1dd279011c6075b3d7a4',
            'kotlin-sam-with-receiver-compiler-plugin-1.3.50.jar': 'd8aa13e98a76adb326fff8c23a66a1fc3f8ab0a1b87c311598191b069f275590',
            'kotlin-scripting-common-1.3.50.jar': 'dd16d71ee2f2f0f3e53cb7f32b30cc9fa1d457ad7d5f428d15a9638c1cc983fc',
            'kotlin-scripting-jvm-1.3.50.jar': 'fa6fa1b78ae0d3e6f950143567cc207e03b64ce6cba842c5149a5226a784c0c2',
            'kotlin-scripting-jvm-host-embeddable-1.3.50.jar': '5084f88eed6fb0850b6aa3148bfeb593744ae04bc26e8b82244a43cbb65ce16c',
            'kotlin-script-runtime-1.3.50.jar': '7ff70c52bf062afbe0a0e78962a9b92b89b0cf4a47a481d24037257e56fb7e4c',
            'kotlin-scripting-compiler-embeddable-1.3.50.jar': 'baa76bdc840a1b7ffc88c5d6e327b75a37118a44e60deae56d71d408fd1c8ac5',
            'kotlin-scripting-compiler-impl-embeddable-1.3.50.jar': '4ab8accc2bff60ae6d767f7e96036f0f8807e21bf758362e023e3ea57c4e490f',
            'kotlin-stdlib-1.3.50.jar': 'e6f05746ee0366d0b52825a090fac474dcf44082c9083bbb205bd16976488d6c',
            'kotlin-stdlib-common-1.3.50.jar': '8ce678e88e4ba018b66dacecf952471e4d7dfee156a8a819760a5a5ff29d323c',
            'kotlin-stdlib-jdk7-1.3.50.jar': '9a026639e76212f8d57b86d55b075394c2e009f1979110751d34c05c5f75d57b',
            'kotlin-stdlib-jdk8-1.3.50.jar': '1b351fb6e09c14b55525c74c1f4cf48942eae43c348b7bc764a5e6e423d4da0c',
            'kotlinx-metadata-jvm-0.1.0.jar' : '9753bb39efef35957c5c15df9a3cb769aabf2cdfa74b47afcb7760e5146be3b5',
            'kryo-2.24.0.jar' : '7e56b32c635058f9aa2820f88919ab702d029cbcd15285da9992e36cc0ae52f2',
            'log4j-over-slf4j-1.7.25.jar' : 'c84c5ce4bbb661369ccd4c7b99682027598a0fb2e3d63a84259dbe5c0bf1f949',
            'minlog-1.2.jar' : 'a678cb1aa8f5d03d901c992c75741841d98a9bc3d55dad02e84d65315c4e60f2',
            'native-platform-0.18.jar' : '8d0cfef773f129d8acb8571c499d5a2894b5fbf599c67e45ee8dd00564ac5699',
            'native-platform-freebsd-amd64-libcpp-0.18.jar' : 'c0c3e6041bdcdca7b803a5999a65b20e4cac7cce2b75349f02e54304273bf25f',
            'native-platform-freebsd-amd64-libstdcpp-0.18.jar' : 'a42bba79f49dbae2b2388b0d7a337dd812e3e4051f87737a2bfd28c5f4954a20',
            'native-platform-freebsd-i386-libcpp-0.18.jar' : '2db5047e3d4bde06b527094e9aabf3220db4fb7b42373eed0b893c602f41faad',
            'native-platform-freebsd-i386-libstdcpp-0.18.jar' : '39e11dfd90a3217cee66da9f2a7a7989b3ccab3dcb6177feca40c859e4c27d15',
            'native-platform-linux-amd64-0.18.jar' : 'd00274173a22575093f57d83b4fdbebd14a2f50bd7739028cea835cb8a119418',
            'native-platform-linux-amd64-ncurses5-0.18.jar' : '336aab51ac3918d52aa712841fcb4772f67f1026d296421b100c1e607e02ae31',
            'native-platform-linux-amd64-ncurses6-0.18.jar' : 'fa8289d841ca9a133b556b5352436548332f73562cca3e6778245e39973aa1a1',
            'native-platform-linux-i386-0.18.jar' : 'b0c81987c6ef3e685b65e6f2cd793ddfe6daea4caedd9d2018be52c8ae478013',
            'native-platform-linux-i386-ncurses5-0.18.jar' : '8aaaae78899d0f705f636452ff1d1dca223557ea41eccf0a5b7e9d1867194361',
            'native-platform-linux-i386-ncurses6-0.18.jar' : '46b29923ef81005d32e54ca91cb7e7623ffec2ed3f32b4762c2915ba34a6efbd',
            'native-platform-linux-aarch64-0.18.jar' : '622d5c2702637e2e345b4bde6f672bb6a4f72b09f47e73e160d76f5d5c588b6a',
            'native-platform-linux-aarch64-ncurses5-0.18.jar' : 'ed2894b11753b866546fe1557bf61dcbc005aac1868548409f115039b513455c',
            'native-platform-osx-amd64-0.18.jar' : 'd9da281edb0ed3482c2cb38f6abe5b1a4e6f6a5c51676a3294b00de09c463d27',
            'native-platform-windows-amd64-0.18.jar' : '4a754eac68b3a8c1f14f39c58ec328339b6400fce0f0ff063041974e3edbb9f4',
            'native-platform-windows-amd64-min-0.18.jar' : '2cbbca810129dbb39af5919e908029fede4e2e691e890ef54e7af75cbfd4e25d',
            'native-platform-windows-i386-0.18.jar' : 	'd06675a6a7f523c604001ba82073dc00201cc9263736f185b996fd833caf71f4',
            'native-platform-windows-i386-min-0.18.jar' : '84487de35b3ca38f60ba689f87881d3ecde8790987fc6f9bb59664b422e82b4c',
            'objenesis-2.6.jar' : '5e168368fbc250af3c79aa5fef0c3467a2d64e5a7bd74005f25d8399aeb0708d',
            'plugins/aether-api-1.13.1.jar' : 'ae8dc80232771f8913febfa410c5719e9ba8ded81fb99788e214fd676dbbe13f',
            'plugins/aether-connector-wagon-1.13.1.jar' : 'df54e8505104228ee7e3fbdead7a7a9cb753b04ca6c9cf60a6b19aee0737f1ec',
            'plugins/aether-impl-1.13.1.jar' : '865511994805827e88f327944a089142bb7f3d88cde271ba3dceb732cb137a93',
            'plugins/aether-spi-1.13.1.jar' : 'd5de4e299be5a79feb1dbe8ff3814034c6e44314b4c00b92ffa8d97576ded5b3',
            'plugins/aether-util-1.13.1.jar' : '687799a0ce988bee9e8eb9ae0ba870300adc0114248ad4a4327bdb625d27e010',
            'plugins/apiguardian-api-1.0.0.jar' : '1f58b77470d8d147a0538d515347dd322f49a83b9e884b8970051160464b65b3',
            'plugins/asm-util-7.1.jar' : 'a24485517596ae1003dcf2329c044a2a861e5c25d4476a695ccaacf560c74d1a',
            'plugins/aws-java-sdk-core-1.11.633.jar' : 'aa4199cd1b52484f9535c36f6ef80f104369aa070912397eeb5c25a41dd1f83e',
            'plugins/aws-java-sdk-kms-1.11.633.jar' : '6a71923945e91f554899481f46fbed66991de8bb9d4ad375c0b8ac802ef28b10',
            'plugins/aws-java-sdk-s3-1.11.633.jar' : 'd4db7809743ee5155da1b9899919369824df6e8ee04db793bd0d73b40b81c2ec',
            'plugins/bcpg-jdk15on-1.61.jar' : 'd31561762756bdc8b70be5c1c72d9e972914e5549eaffe25e684b73aa15d1f63',
            'plugins/bcprov-jdk15on-1.61.jar' : 'dba6e408f205215ad1a89b70b37353d3cdae4ec61037e1feee885704e2413458',
            'plugins/bsh-2.0b6.jar' : 'a17955976070c0573235ee662f2794a78082758b61accffce8d3f8aedcd91047',
            'plugins/commons-codec-1.11.jar' : 'e599d5318e97aa48f42136a2927e6dfa4e8881dff0e6c8e3109ddbbff51d7b7d',
            'plugins/dd-plist-1.21.jar' : '019c61abd93ecf614e3d214e9fed942dcf47d9d2d9548fe59d70f0840ba32fb6',
            'plugins/google-api-client-1.25.0.jar' : '24e1a69d6c04e6e72e3e16757d46d32daa7dd43cb32c3895f832f25358be1402',
            'plugins/google-api-services-storage-v1-rev136-1.25.0.jar' : 'c517a94e5ff2670470fc8c3ae690e7d0d28d210276e6f2228aaafead994c59d1',
            'plugins/google-http-client-1.25.0.jar' : 'fb7d80a515da4618e2b402e1fef96999e07621b381a5889ef091482c5a3e961d',
            'plugins/google-http-client-jackson2-1.25.0.jar' : 'f9e7e0d318860a2092d70b56331976280c4e9348a065ede3b99c92aa032fd853',
            'plugins/google-oauth-client-1.25.0.jar' : '7e2929133d4231e702b5956a7e5dc8347a352acc1e97082b40c3585b81cd3501',
            'plugins/gson-2.8.5.jar' : '233a0149fc365c9f6edbd683cfe266b19bdc773be98eabdaf6b3c924b48e7d81',
            'plugins/hamcrest-core-1.3.jar' : '66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9',
            'plugins/httpclient-4.5.10.jar' : '38b9f16f504928e4db736a433b9cd10968d9ec8d6f5d0e61a64889a689172134',
            'plugins/httpcore-4.4.12.jar' : 'ab765334beabf0ea024484a5e90a7c40e8160b145f22d199e11e27f68d57da08',
            'plugins/ion-java-1.0.2.jar' : '0d127b205a1fce0abc2a3757a041748651bc66c15cf4c059bac5833b27d471a5',
            'plugins/ivy-2.3.0.jar' : 'ff3543305c62f23d1a4cafc66fab9c9f55ea169ccf2b6c040d3fa23254b86b18',
            'plugins/jackson-annotations-2.9.8.jar' : 'fdca896161766ca4a2c3e06f02f6a5ede22a5b3a55606541cd2838eace08ca23',
            'plugins/jackson-core-2.9.8.jar' : 'd934dab0bd48994eeea2c1b493cb547158a338a80b58c4fbc8e85fb0905e105f',
            'plugins/jackson-databind-2.9.8.jar' : '2351c3eba73a545db9079f5d6d768347ad72666537362c8220fe3e950a55a864',
            'plugins/jatl-0.2.3.jar' : '93ee3810f7275244f5f6311d50c587d8fe43ab5047dfa3291aa96fe50587501c',
            'plugins/jaxb-impl-2.3.1.jar' : 'e6c9e0f1830fd5f7c30c25ecf5e2046c5668b06d304add89d2f027d5072297d0',
            'plugins/jcifs-1.3.17.jar' : '3b077938f676934bc20bde821d1bdea2cd6d55d96151218b40fd159532f45061',
            'plugins/jcommander-1.72.jar' : 'e0de160b129b2414087e01fe845609cd55caec6820cfd4d0c90fabcc7bdb8c1e',
            'plugins/jmespath-java-1.11.633.jar' : '6ca34bef5587d6f3986199df310fd5b54f139c700f4bf14ed8c258ad3e1d0e77',
            'plugins/joda-time-2.10.jar' : 'c4d50dae4d58c3031475d64ae5eafade50f1861ca1553252aa7fd176d56e4eec',
            'plugins/jsch-0.1.54.jar' : '92eb273a3316762478fdd4fe03a0ce1842c56f496c9c12fe1235db80450e1fdb',
            'plugins/junit-4.12.jar' : '59721f0805e223d84b90677887d9ff567dc534d7c502ca903c0c2b17f05c116a',
            'plugins/junit-platform-commons-1.3.1.jar' : '457d8e1c0c80d1e320a792ec35e7c180694ba05182d7ecf7dabdb4e5a8a12fe2',
            'plugins/junit-platform-engine-1.3.1.jar' : '303d0546c3e950cc3beaca00dfcbf2632d4eca530e4e446391bf193cbc2a71a3',
            'plugins/junit-platform-launcher-1.3.1.jar' : '6f698076c33cf27d2b80de11506031b625733aab7f18e2d4c5d15803f27231dd',
            'plugins/jzlib-1.1.3.jar' : '89b1360f407381bf61fde411019d8cbd009ebb10cff715f3669017a031027560',
            'plugins/maven-aether-provider-3.0.4.jar' : '33ff4aabbd0d02e4dd8279cda8f366c69915302bc4bb97bc01814a985f5c0643',
            'plugins/maven-artifact-3.0.4.jar' : '3c199a96af9550872724f41c053d7839dfcc6512e7704fa16c675363c4146796',
            'plugins/maven-compat-3.0.4.jar' : 'a2d02378d413d9e87833780820163902c5be4b0f3c44b65de7608f47cd94599f',
            'plugins/maven-core-3.0.4.jar' : '3dd795c0ad9742a0be65a2a5ec22428d59dd2a891a7565ae94f64661e3740528',
            'plugins/maven-model-3.0.4.jar' : '26b6825ea73ac4d7b1a6f5e62ac1c11b0fc272504da6dde9ba8f894cd847e1c1',
            'plugins/maven-model-builder-3.0.4.jar' : 'b4f1d3ae53c290e1ae45694c5ce2d17bf8d577ff5ece0f9aa0cffe151a6ef4e7',
            'plugins/maven-plugin-api-3.0.4.jar' : '4e5ee7f7ab7e43f691788489e59f2da4a322e3e35f2a2d8b714ad929f624eead',
            'plugins/maven-repository-metadata-3.0.4.jar' : 'a25c4db27cffda9e9229db168b1190d6a3e5439f3f67d6afec3df9470e0752d5',
            'plugins/maven-settings-3.0.4.jar' : '3e3df17f5df5e4ce1e7b7f2011c57d61d328e65678542ade2048f0d0fa295f09',
            'plugins/maven-settings-builder-3.0.4.jar' : 'a38a54ec1e69a30ddfc14434e0aec2764fc268668abcc0e132d86692a5dce3e4',
            'plugins/nekohtml-1.9.22.jar' : '452978e8b6667c7b8357fd3f0a2f2f405e4560a7148143a69181735da5d19045',
            'plugins/opentest4j-1.1.1.jar' : 'f106351abd941110226745ed103c85863b3f04e9fa82ddea1084639ae0c5336c',
            'plugins/org.eclipse.jgit-5.0.3.201809091024-r.jar' : '5b3a2dacf8619884bf2ca998c0d7a9980aeaf24ace664e9c2b1adbee8e913e19',
            'plugins/plexus-cipher-1.7.jar' : '114859861ff10f987b880d6f34e3215274af3cc92b3a73831c84d596e37c6511',
            'plugins/plexus-classworlds-2.5.1.jar' : 'de9ce33b29088c2db7c3f55ddc061c2a7a72f9c93c28faad62cc15aee26b6888',
            'plugins/plexus-component-annotations-1.5.5.jar' : '4df7a6a7be64b35bbccf60b5c115697f9ea3421d22674ae67135dde375fcca1f',
            'plugins/plexus-container-default-1.7.1.jar' : 'f3f61952d63675ef61b42fa4256c1635749a5bc17668b4529fccde0a29d8ee19',
            'plugins/plexus-interpolation-1.14.jar' : '7fc63378d3e84663619b9bedace9f9fe78b276c2be3c62ca2245449294c84176',
            'plugins/plexus-sec-dispatcher-1.3.jar' : '3b0559bb8432f28937efe6ca193ef54a8506d0075d73fd7406b9b116c6a11063',
            'plugins/plexus-utils-3.1.0.jar' : '0ffa0ad084ebff5712540a7b7ea0abda487c53d3a18f78c98d1a3675dab9bf61',
            'plugins/pmaven-common-0.8-20100325.jar' : '129306abcdb337cd53e710aadafaa226f017997e6bc81c2cb50deb00916ca8d8',
            'plugins/pmaven-groovy-0.8-20100325.jar' : '87891a373d079fd370e859b1f5bd0579d8b4a68e515fa8bbae91301c28f48d57',
            'plugins/rhino-1.7.10.jar' : '38eb3000cf56b8c7559ee558866a768eebcbf254174522d6404b7f078f75c2d4',
            'plugins/simple-4.1.21.jar' : '8e6439fcc1f2fe87b5dfc20eb13289acba8047e9cfadf45ad3dc5c57a9bcd054',
            'plugins/snakeyaml-1.17.jar' : '5666b36f9db46f06dd5a19d73bbff3b588d5969c0f4b8848fde0f5ec849430a5',
            'plugins/testng-6.3.1.jar' : '57ed8e83e357c3838daad81b789a2753227fee8cfb86aa31a61b765c4093d6ad',
            'plugins/wagon-file-3.0.0.jar' : '63324036899559f94154e6f845e62453a1f202c1b21b80060f2d88a05a05a272',
            'plugins/wagon-http-3.0.0.jar' : '9f68fed6684c2245a62d5d3c8b4954b882562797e96b15ce0f18514c543fb999',
            'plugins/wagon-http-shared-3.0.0.jar' : '40c51265b3ed90b6919c08dcdf3620dc614894ef60bacdf4c34bdbe295b44c49',
            'plugins/wagon-provider-api-3.0.0.jar' : '04de4d2f39178998ef3ce5f6d91a358363ad3f5270e897d5547321ea69fa2992',
            'plugins/xbean-reflect-3.7.jar' : '104e5e9bb5a669f86722f32281960700f7ec8e3209ef51b23eb9b6d23d1629cb',
            'plugins/xercesImpl-2.12.0.jar' : 'b50d3a4ca502faa4d1c838acb8aa9480446953421f7327e338c5dda3da5e76d0',
            'slf4j-api-1.7.25.jar' : '18c4a0095d5c1da6b817592e767bb23d29dd2f560ad74df75ff3961dbde25b79',
            'trove4j-1.0.20181211.jar' : 'affb7c85a3c87bdcf69ff1dbb84de11f63dc931293934bc08cd7ab18de083601',
            'xml-apis-1.4.01.jar' : 'a840968176645684bb01aed376e067ab39614885f9eee44abe35a5f20ebe7fad',
        ]

        def libDir = unpackDistribution().file('lib')
        def jars = collectJars(libDir)
        def filtered = jars.grep { jar ->
            // Filter out any excluded jars
            !excluded.any { jar.name.startsWith(it) }
        }
        Map<String, TestFile> depJars = filtered.collectEntries { [libDir.relativePath(it), it] }

        when:
        def errors = []
        depJars.each { String jarPath, TestFile jar ->
            def expected = expectedHashes[jarPath]
            def actual = jar.sha256Hash
            if (expected != actual) {
                errors << "SHA-256 hash does not match for ${jarPath}: expected=${expected}, actual=${actual}"
            }
        }
        then:
        errors.empty

        when:
        def added = depJars.keySet() - expectedHashes.keySet()
        def removed = expectedHashes.keySet() - depJars.keySet()
        then:
        (added + removed).isEmpty()
    }

    @Issue(['https://github.com/gradle/gradle/issues/9990', 'https://github.com/gradle/gradle/issues/10038'])
    def "validate dependency archives"() {
        when:
        def jars = collectJars(unpackDistribution())
        then:
        jars != []

        when:
        def invalidArchives = jars.findAll {
            new ZipFile(it).withCloseable {
                def names = it.entries()*.name
                names.size() != names.toUnique().size()
            }
        }
        then:
        invalidArchives == []
    }

    private static def collectJars(TestFile file, Collection<File> acc = []) {
        if (file.name.endsWith('.jar')) {
            acc.add(file)
        }
        if (file.isDirectory()) {
            file.listFiles().each { f -> collectJars(f, acc) }
        }
        acc
    }
}
