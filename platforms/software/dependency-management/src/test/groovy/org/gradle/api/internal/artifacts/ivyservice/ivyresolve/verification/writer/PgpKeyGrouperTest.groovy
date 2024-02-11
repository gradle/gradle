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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import spock.lang.Specification

class PgpKeyGrouperTest extends Specification {
    private DependencyVerifierBuilder builder = new DependencyVerifierBuilder()
    private DependencyVerifier verifier
    private PgpKeyGrouper pgpKeyGrouper

    private static final String KEY_1 = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
    private static final String KEY_2 = 'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB'

    def "common prefix for groups #groups == #expected"() {
        expect:
        PgpKeyGrouper.tryComputeCommonPrefixes(groups) == expected

        where:
        groups                                                                            | expected
        ["org", "com"]                                                                    | []
        ["org.foo", "com"]                                                                | []
        ["org.foo", "org.bar"]                                                            | [] // require at least 2 group items
        ["org.foo.a", "org.foo"]                                                          | [["org", "foo"]]
        ["org.foo.a", "org.foo.b"]                                                        | [["org", "foo"]]
        ["org.foo.a.b", "org.foo.a.c"]                                                    | [["org", "foo", "a"]]
        ["org.foo.a.b", "org.foo"]                                                        | [["org", "foo"]]
        ["org.foo.a.b", "org.foo.c.d"]                                                    | [["org", "foo"]]
        ["org.foo.a.b", "org.bar.baz"]                                                    | []
        ["org.a.1", "org.a.2", "org.b.1", "org.b.2"]                                      | [["org", "a"], ["org", "b"]]
        ["org.a.1", "org.a.2", "org.a.1.c", "org.b.1", "org.b.2"]                         | [["org", "a", "1"], ["org", "b"]]

    }

    def "more complex grouping"() {
        expect:
        PgpKeyGrouper.tryComputeCommonPrefixes([
            "org.codehaus.groovy.modules",
            "io.github.http-builder-ng",
            "org.jetbrains.intellij.deps",
            "org.jetbrains.kotlinx",
            "info.picocli",
            "com.github.javaparser",
            "io.github.http-builder-ng.test"
        ]) as Set == [["org", "jetbrains"], ["io","github", "http-builder-ng"]] as Set
    }

    def "groups entries which have the same module component id"() {
        grouper {
            entry("org", "foo", "1.0", "foo-1.0.jar").addVerifiedKey(KEY_1)
            entry("org", "foo", "1.0", "foo-1.0.pom").addVerifiedKey(KEY_1)
        }

        when:
        executeGrouping()

        then:
        verifier.configuration.trustedKeys*.keyId == [KEY_1]
        verifier.verificationMetadata.empty
    }

    def "groups entries which have the same module id"() {
        grouper {
            entry("org", "foo", "1.0", "foo-1.0.jar").addVerifiedKey(KEY_1)
            entry("org", "foo", "1.0", "foo-1.0.pom").addVerifiedKey(KEY_1)
            entry("org", "foo", "1.1", "foo-1.1.jar").addVerifiedKey(KEY_1)
            entry("org", "foo", "1.1", "foo-1.1.pom").addVerifiedKey(KEY_1)
        }

        when:
        executeGrouping()

        then:
        verifier.configuration.trustedKeys*.keyId == [KEY_1]
    }

    def "doesn't group entries which have the same module id but different keys"() {
        grouper {
            entry("org", "foo", "1.0", "foo-1.0.jar").addVerifiedKey(KEY_1)
            entry("org", "foo", "1.0", "foo-1.0.pom").addVerifiedKey(KEY_1)
            entry("org", "foo", "1.1", "foo-1.1.jar").addVerifiedKey(KEY_2)
            entry("org", "foo", "1.1", "foo-1.1.pom").addVerifiedKey(KEY_2)
        }

        when:
        executeGrouping()

        then:
        def keys = verifier.configuration.trustedKeys
        keys*.keyId == [KEY_1, KEY_2]
        keys[0].version == '1.0'
        keys[1].version == '1.1'
    }

    def "groups entries which have the same group id"() {
        grouper {
            entry("org", "foo", "1.0", "foo-1.0.jar").addVerifiedKey(KEY_1)
            entry("org", "foo", "1.0", "foo-1.0.pom").addVerifiedKey(KEY_1)
            entry("org", "bar", "1.1", "bar-1.1.jar").addVerifiedKey(KEY_1)
            entry("org", "bar", "1.1", "bar-1.1.pom").addVerifiedKey(KEY_1)
        }

        when:
        executeGrouping()

        then:
        def keys = verifier.configuration.trustedKeys
        keys*.keyId == [KEY_1]
        keys[0].group == 'org'
        keys[0].name == null
        keys[0].version == null
        keys[0].fileName == null
    }

    def "groups entries which have a common group prefix"() {
        grouper {
            entry("org.group.a", "foo", "1.0", "foo-1.0.jar").addVerifiedKey(KEY_1)
            entry("org.group.a", "foo", "1.0", "foo-1.0.pom").addVerifiedKey(KEY_1)
            entry("org.group.b", "bar", "1.1", "bar-1.1.jar").addVerifiedKey(KEY_1)
            entry("org.group.b", "bar", "1.1", "bar-1.1.pom").addVerifiedKey(KEY_1)
        }

        when:
        executeGrouping()

        then:
        def keys = verifier.configuration.trustedKeys
        keys*.keyId == [KEY_1]
        keys[0].group == '^org[.]group($|([.].*))'
        keys[0].name == null
        keys[0].version == null
        keys[0].fileName == null
        keys[0].regex
    }

    def "does not attempt grouping when it exists already"() {
        def trustedKey = new DependencyVerificationConfiguration.TrustedKey(KEY_1, "org.*", null, null, null, true)
        builder.addTrustedKey(KEY_1, "org.*", null, null, null, true)

        grouper {
            entry("org.group.a", "foo", "1.0", "foo-1.0.jar").addVerifiedKey(KEY_1)
            entry("org.group.a", "foo", "1.0", "foo-1.0.pom").addVerifiedKey(KEY_1)
            entry("org.group.b", "bar", "1.1", "bar-1.1.jar").addVerifiedKey(KEY_1)
            entry("org.group.b", "bar", "1.1", "bar-1.1.pom").addVerifiedKey(KEY_1)
        }

        when:
        executeGrouping()

        then:
        verifier.configuration.trustedKeys == [trustedKey]
    }

    private void executeGrouping() {
        pgpKeyGrouper.performPgpKeyGrouping()
        verifier = builder.build()
    }

    private void grouper(@DelegatesTo(value = EntriesBuilder, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def entries = new EntriesBuilder()
        spec.delegate = entries
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        pgpKeyGrouper = new PgpKeyGrouper(builder, entries.entries)
    }

    private static class EntriesBuilder {
        private final Set<PgpEntry> entries = new LinkedHashSet<>()

        PgpEntry entry(String group = "org", String name = null, String version = null, String file = null) {
            def entry = pgpEntry(group, name, version, file)
            entries.add(entry)
            entry
        }
    }

    private static pgpEntry(String group = "org", String name = null, String version = null, String file = null) {
        new PgpEntry(artifact(group, name, version, file), ArtifactVerificationOperation.ArtifactKind.REGULAR, new File("dummy"), null)
    }

    private static ModuleComponentFileArtifactIdentifier artifact(String group = "org", String name = "foo", String version = "1.0", String file = "foo-1.0.jar") {
        new ModuleComponentFileArtifactIdentifier(
            new DefaultModuleComponentIdentifier(
                DefaultModuleIdentifier.newId(group, name),
                version
            ),
            file
        )
    }
}
