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

package org.gradle.resolve.scenarios

import groovy.transform.Canonical
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint

/**
 * A comprehensive set of test cases for dependency resolution of a single module version, given a set of input selectors.
 */
class VersionRangeResolveTestScenarios {
    public static final REJECTED = "REJECTED"
    public static final FAILED = "FAILED"
    public static final IGNORE = "IGNORE"

    public static final FIXED_7 = fixed(7)
    public static final FIXED_9 = fixed(9)
    public static final FIXED_10 = fixed(10)
    public static final FIXED_11 = fixed(11)
    public static final FIXED_12 = fixed(12)
    public static final FIXED_13 = fixed(13)
    public static final PREFER_11 = prefer(11)
    public static final PREFER_12 = prefer(12)
    public static final RANGE_7_8 = range(7, 8)
    public static final RANGE_10_11 = range(10, 11)
    public static final RANGE_10_12 = range(10, 12)
    public static final RANGE_10_14 = range(10, 14)
    public static final RANGE_10_16 = range(10, 16)
    public static final RANGE_11_12 = range(11, 12)
    public static final RANGE_12_14 = range(12, 14)
    public static final RANGE_13_14 = range(13, 14)
    public static final RANGE_14_16 = range(14, 16)

    public static final DYNAMIC_PLUS = dynamic('+')
    public static final DYNAMIC_LATEST = dynamic( 'latest.integration')

    public static final REJECT_11 = reject(11)
    public static final REJECT_12 = reject(12)
    public static final REJECT_13 = reject(13)

    public static final StrictPermutationsProvider SCENARIOS_PREFER = StrictPermutationsProvider.check(
        versions: [FIXED_11, PREFER_12],
        expectedNoStrict: "12",
        expectedStrict: ["11", IGNORE]
    ).and(
        versions: [FIXED_12, PREFER_11],
        expectedNoStrict: "12",
        expectedStrict: ["12", IGNORE]
    ).and(
        versions: [RANGE_10_12, PREFER_11],
        expectedNoStrict: "11",
        expectedStrict: ["11", IGNORE]
    ).and(
        versions: [RANGE_10_11, PREFER_12],
        expectedNoStrict: "11",
        expectedStrict: ["11", IGNORE]
    ).and(
        versions: [RANGE_12_14, PREFER_11],
        expectedNoStrict: "13",
        expectedStrict: ["13", IGNORE]
    )

    public static final StrictPermutationsProvider SCENARIOS_TWO_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_7, FIXED_13],
        expectedNoStrict: "13",
        expectedStrict: [REJECTED, "13"]
    ).and(
        versions: [FIXED_12, FIXED_13],
        expectedNoStrict: "13",
        expectedStrict: [REJECTED, "13"]
    ).and(
        versions: [FIXED_12, RANGE_10_11],
        expectedNoStrict: "12",
        expectedStrict: ["12", REJECTED]
    ).and(
        versions: [FIXED_12, RANGE_10_14],
        expectedNoStrict: "12",
        expectedStrict: ["12", "12"]
    ).and(
        versions: [FIXED_12, RANGE_13_14],
        expectedNoStrict: "13",
        expectedStrict: [REJECTED, "13"]
    ).and(
        versions: [FIXED_12, RANGE_7_8],  // No version satisfies the range [7,8]
        expectedNoStrict: FAILED,
        expectedStrict: [FAILED, FAILED]
    ).and(
        versions: [FIXED_12, RANGE_14_16], // No version satisfies the range [14,16]
        expectedNoStrict: FAILED,
        expectedStrict: [FAILED, FAILED]
    ).and(
        versions: [RANGE_10_11, FIXED_10],
        expectedNoStrict: "10",
        expectedStrict: ["10", "10"]
    ).and(
        versions: [RANGE_10_14, FIXED_13],
        expectedNoStrict: "13",
        expectedStrict: ["13", "13"]
    ).and(
        versions: [RANGE_10_14, RANGE_10_11],
        expectedNoStrict: "11",
        expectedStrict: ["11", "11"]
    ).and(
        versions: [RANGE_10_14, RANGE_10_16],
        expectedNoStrict: "13",
        expectedStrict: ["13", "13"]
    ).and(
        versions: [DYNAMIC_PLUS, FIXED_11],
        expectedNoStrict: "11",
        expectedStrict: [IGNORE, "11"]
    ).and(
        versions: [DYNAMIC_PLUS, RANGE_10_12],
        expectedNoStrict: "12",
        expectedStrict: [IGNORE, "12"]
    ).and(
        versions: [DYNAMIC_PLUS, RANGE_10_16],
        expectedNoStrict: "13",
        expectedStrict: [IGNORE, "13"]
    ).and(
        versions: [DYNAMIC_LATEST, FIXED_11],
        expectedNoStrict: "13",
        expectedStrict: [IGNORE, "11"]
    ).and(
        versions: [DYNAMIC_LATEST, RANGE_10_12],
        expectedNoStrict: "13",
        expectedStrict: [IGNORE, "12"]
    ).and(
        versions: [DYNAMIC_LATEST, RANGE_10_16],
        expectedNoStrict: "13",
        expectedStrict: [IGNORE, "13"]
    )

    public static final StrictPermutationsProvider SCENARIOS_DEPENDENCY_WITH_REJECT = StrictPermutationsProvider.check(
        versions: [FIXED_12, REJECT_11],
        expectedNoStrict: "12"
    ).and(
        versions: [FIXED_12, REJECT_12],
        expectedNoStrict: REJECTED
    ).and(
        versions: [FIXED_12, REJECT_13],
        expectedNoStrict: "12"
    ).and(
        versions: [RANGE_10_12, REJECT_11],
        expectedNoStrict: "12"
    ).and(
        versions: [RANGE_10_12, REJECT_12],
        expectedNoStrict: "11"
    ).and(
        versions: [RANGE_10_12, REJECT_13],
        expectedNoStrict: "12"
    )

    public static final StrictPermutationsProvider SCENARIOS_THREE_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_10, FIXED_12, FIXED_13],
        expectedNoStrict: "13",
        expectedStrict: [REJECTED, REJECTED, "13"]
    ).and(
        versions: [FIXED_10, FIXED_12, RANGE_10_14],
        expectedNoStrict: "12",
        expectedStrict: [REJECTED, "12", "12"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_14],
        expectedNoStrict: "10",
        expectedStrict: ["10", "10", "10"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_13_14],
        expectedNoStrict: "13",
        expectedStrict: [REJECTED, REJECTED, "13"]
    ).and(
        versions: [FIXED_10, RANGE_11_12, RANGE_10_14],
        expectedNoStrict: "12",
        expectedStrict: [REJECTED, "12", "12"]
    ).and(
        versions: [RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expectedNoStrict: "11",
        expectedStrict: ["11", "11", "11"]
    ).and(
        versions: [RANGE_10_11, RANGE_10_12, RANGE_13_14],
        expectedNoStrict: "13",
        expectedStrict: [REJECTED, REJECTED, "13"]
    ).and(
        versions: [FIXED_10, FIXED_10, FIXED_12],
        expectedNoStrict: "12",
        expectedStrict: [REJECTED, REJECTED, "12"]
    ).and(
        versions: [FIXED_10, FIXED_12, RANGE_12_14],
        expectedNoStrict: "12",
        expectedStrict: [REJECTED, "12", "12"]
    )

    public static final StrictPermutationsProvider SCENARIOS_WITH_REJECT = StrictPermutationsProvider.check(
        versions: [FIXED_11, FIXED_12, REJECT_11],
        expectedNoStrict: "12",
    ).and(
        versions: [FIXED_11, FIXED_12, REJECT_12],
        expectedNoStrict: REJECTED,
    ).and(
        versions: [FIXED_11, FIXED_12, REJECT_13],
        expectedNoStrict: "12",

    ).and(
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_11],
        expectedNoStrict: "12",
    ).and(
        ignore: true, // This will require resolving RANGE_10_14 with the knowledge that FIXED_12 rejects < "12".
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_12],
        expectedNoStrict: "13",
    ).and(
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_13],
        expectedNoStrict: "12",
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_11],
        expectedNoStrict: "13",
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_12],
        expectedNoStrict: "13",
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_13],
        expectedNoStrict: REJECTED,
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_11],
        expectedNoStrict: "10",
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_12],
        expectedNoStrict: "11",
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_13],
        expectedNoStrict: "11",
    )

    public static final StrictPermutationsProvider SCENARIOS_FOUR_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_9, FIXED_10, FIXED_11, FIXED_12],
        expectedNoStrict: "12"
    ).and(
        versions: [FIXED_10, RANGE_10_11, FIXED_12, RANGE_12_14],
        expectedNoStrict: "12"
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_12, RANGE_13_14],
        expectedNoStrict: "13"
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expectedNoStrict: "11"
    )
    private static RenderableVersion fixed(int version) {
        def vs = new SimpleVersion()
        vs.version = "${version}"
        return vs
    }

    private static RenderableVersion prefer(int version) {
        def vs = new PreferVersion()
        vs.version = "${version}"
        return vs
    }

    private static RenderableVersion dynamic(String version) {
        def vs = new SimpleVersion()
        vs.version = version
        return vs
    }

    private static RenderableVersion range(int low, int high) {
        def vs = new SimpleVersion()
        vs.version = "[${low},${high}]"
        return vs
    }

    private static RenderableVersion reject(int version) {
        def vs = new RejectVersion()
        vs.version = version
        vs
    }

    private static RenderableVersion strict(RenderableVersion input) {
        def v = new StrictVersion()
        v.version = input.version
        v
    }

    interface RenderableVersion {
        String getVersion()

        VersionConstraint getVersionConstraint()

        String render()
    }

    static class SimpleVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            new DefaultMutableVersionConstraint(version, false)
        }

        @Override
        String render() {
            "'org:foo:${version}'"
        }

        @Override
        String toString() {
            return version
        }
    }

    static class StrictVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            new DefaultMutableVersionConstraint(version, true)
        }

        @Override
        String render() {
            return "('org:foo') { version { strictly '${version}' } }"
        }

        @Override
        String toString() {
            return "strictly(" + version + ")"
        }
    }

    static class PreferVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            DefaultImmutableVersionConstraint.of(version)
        }

        @Override
        String render() {
            return "('org:foo') { version { prefer '${version}' } }"
        }

        @Override
        String toString() {
            return "prefer(" + version + ")"
        }
    }

    static class RejectVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            DefaultImmutableVersionConstraint.of("", "", [version])
        }

        @Override
        String render() {
            "('org:foo') { version { reject '${version}' } }"
        }

        @Override
        String toString() {
            return "reject " + version
        }
    }

    static class StrictPermutationsProvider implements Iterable<Candidate> {
        private final List<Batch> batches = []
        private int batchCount

        static StrictPermutationsProvider check(Map config) {
            new StrictPermutationsProvider().and(config)
        }

        StrictPermutationsProvider and(Map config) {
            assert config.versions
            assert config.expectedNoStrict

            ++batchCount
            if (!config.ignore) {
                List<RenderableVersion> versions = config.versions
                String expected = config.expectedNoStrict
                List<String> expectedStrict = config.expectedStrict
                List<Batch> iterations = []
                String batchName = config.description ?: "#${batchCount} (${versions})"
                iterations.add(new Batch(batchName, versions, expected))
                if (expectedStrict) {
                    versions.size().times { idx ->
                        def expectedStrictResolution = expectedStrict[idx]
                        if (expectedStrictResolution != IGNORE) {
                            iterations.add(new Batch(batchName, versions.withIndex().collect { RenderableVersion version, idx2 ->
                                if (idx == idx2) {
                                    strict(version)
                                } else {
                                    version
                                }
                            }, expectedStrictResolution))
                        }
                    }
                }
                batches.addAll(iterations)
            }

            this
        }

        @Override
        Iterator<Candidate> iterator() {
            new PermutationIterator()
        }

        @Canonical
        static class Batch {
            String batchName
            List<RenderableVersion> versions
            String expected
        }

        class PermutationIterator implements Iterator<Candidate> {
            Iterator<Batch> batchesIterator = batches.iterator()
            String currentBatch
            Iterator<List<RenderableVersion>> current
            String expected

            @Override
            boolean hasNext() {
                batchesIterator.hasNext() || current?.hasNext()
            }

            @Override
            Candidate next() {
                if (current?.hasNext()) {
                    return new Candidate(batch: currentBatch, candidates: current.next() as RenderableVersion[], expected: expected)
                }
                Batch nextBatch = batchesIterator.next()
                expected = nextBatch.expected
                current = nextBatch.versions.permutations().iterator()
                currentBatch = nextBatch.batchName
                return next()
            }
        }

        static class Candidate {
            String batch
            RenderableVersion[] candidates
            String expected

            @Override
            String toString() {
                batch + ": " + candidates.collect { it.toString() }.join(' & ') + " -> ${expected}"
            }
        }
    }
}
