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

    public static final EMPTY = empty()
    public static final FIXED_7 = fixed(7)
    public static final FIXED_9 = fixed(9)
    public static final FIXED_10 = fixed(10)
    public static final FIXED_11 = fixed(11)
    public static final FIXED_12 = fixed(12)
    public static final FIXED_13 = fixed(13)
    public static final PREFER_11 = prefer(11)
    public static final PREFER_12 = prefer(12)
    public static final PREFER_13 = prefer(13)
    public static final PREFER_7_8 = prefer(7, 8)
    public static final PREFER_10_11 = prefer(10, 11)
    public static final PREFER_10_12 = prefer(10, 12)
    public static final PREFER_10_14 = prefer(10, 14)
    public static final PREFER_14_16 = prefer(14, 16)
    public static final RANGE_7_8 = range(7, 8)
    public static final RANGE_10_11 = range(10, 11)
    public static final RANGE_10_12 = range(10, 12)
    public static final RANGE_10_14 = range(10, 14)
    public static final RANGE_10_16 = range(10, 16)
    public static final RANGE_10_OR_HIGHER = dynamic("[10,)")
    public static final RANGE_11_OR_HIGHER = dynamic("[11,)")
    public static final RANGE_12_OR_LOWER = dynamic("(,12]")
    public static final RANGE_MORE_THAN_10 = dynamic("(10,)")
    public static final RANGE_LESS_THAN_12 = dynamic("(,12)")
    public static final RANGE_10_12_EXCLUSIVE = dynamic("(10,12)")
    public static final RANGE_11_12 = range(11, 12)
    public static final RANGE_11_13 = range(11, 13)
    public static final RANGE_12_14 = range(12, 14)
    public static final RANGE_13_14 = range(13, 14)
    public static final RANGE_14_16 = range(14, 16)
    public static final RANGE_10_12_AND_PREFER_11 = requireAndPrefer(10, 12, 11)

    public static final DYNAMIC_PLUS = dynamic('+')
    public static final DYNAMIC_LATEST = dynamic('latest.integration')

    public static final REJECT_11 = reject("11")
    public static final REJECT_12 = reject("12")
    public static final REJECT_13 = reject("13")
    public static final REJECT_10_11 = reject("[10,11]")
    public static final REJECT_9_14 = reject("[9,14]")
    public static final REJECT_ALL = reject("+")
    public static final REJECT_10_OR_HIGHER = reject("[10,)")

    public static final StrictPermutationsProvider SCENARIOS_SINGLE = StrictPermutationsProvider.check(
        versions: [FIXED_12],
        expected: "12"
    ).and(
        versions: [PREFER_12],
        expected: "12"
    ).and(
        versions: [RANGE_10_12],
        expected: "12"
    ).and(
        versions: [RANGE_10_12_AND_PREFER_11],
        expected: "11"
    )

    public static final StrictPermutationsProvider SCENARIOS_EMPTY = StrictPermutationsProvider.check(
        versions: [EMPTY, FIXED_12],
        expected: "12"
    ).and(
        versions: [EMPTY, PREFER_12],
        expected: "12"
    ).and(
        versions: [EMPTY, RANGE_10_12],
        expected: "12"
    ).and(
        versions: [EMPTY, EMPTY],
        expected: ""
    ).and(
        versions: [EMPTY, EMPTY, FIXED_12],
        expected: "12"
    )

    public static final StrictPermutationsProvider SCENARIOS_PREFER_BATCH1 = StrictPermutationsProvider.check(
        versions: [PREFER_11, PREFER_12],
        expected: "12",
        conflicts: true
    ).and(
        versions: [PREFER_11, PREFER_10_12],
        expected: "12",
        conflicts: true
    ).and(
        versions: [PREFER_12, PREFER_10_11],
        expected: "12",
        conflicts: true
    ).and(
        versions: [PREFER_11, PREFER_12, PREFER_10_14],
        expected: "13",
        conflicts: true
    ).and(
        versions: [PREFER_10_11, PREFER_12, PREFER_10_14],
        expected: "13",
        conflicts: true
    ).and(
        versions: [PREFER_10_11, PREFER_10_12, PREFER_10_14],
        expected: "13"
    ).and(
        versions: [PREFER_12, FIXED_11],
        expected: "11",
        expectedStrict: [IGNORE, "11"]
    ).and(
        versions: [PREFER_11, FIXED_12],
        expected: "12",
        expectedStrict: [IGNORE, "12"],
        conflicts: true // TODO Should not be any conflict resolution here
    ).and(
        versions: [FIXED_11, PREFER_12, RANGE_10_14],
        expected: "11",
        expectedStrictSingle: ["11", IGNORE, "11"],
        expectedStrictMulti: ["11", IGNORE, IGNORE]
    ).and(
        versions: [PREFER_11, PREFER_13, RANGE_10_12],
        expected: "11",
        expectedStrictSingle: [IGNORE, IGNORE, "11"],
        expectedStrictMulti: [IGNORE, IGNORE, IGNORE],
    )

    public static final StrictPermutationsProvider SCENARIOS_PREFER_BATCH2 = StrictPermutationsProvider.check(
        versions: [PREFER_12, RANGE_10_12],
        expected: "12",
        expectedStrict: [IGNORE, "12"]
    ).and(
        versions: [PREFER_12, RANGE_10_14],
        expected: "12",
        expectedStrictSingle: [IGNORE, "12"],
        expectedStrictMulti: [IGNORE, ["12", "13"]]
    ).and(
        versions: [PREFER_11, RANGE_12_14],
        expected: "13",
        expectedStrict: [IGNORE, "13"],
        conflicts: true // TODO Should not be any conflict resolution here
    ).and(
        versions: [PREFER_11, PREFER_12, RANGE_10_14],
        expected: "12",
        expectedStrictSingle: [IGNORE, IGNORE, "12"],
        expectedStrictMulti: [IGNORE, IGNORE, IGNORE]
    ).and(
        versions: [PREFER_11, RANGE_10_11, PREFER_13, RANGE_10_14],
        expected: "11"
    ).and(
        versions: [PREFER_7_8, FIXED_12],  // No version satisfies the range [7,8]
        expected: FAILED
    ).and(
        versions: [PREFER_14_16, FIXED_12], // No version satisfies the range [14,16]
        expected: FAILED
    ).and(
        versions: [PREFER_12, RANGE_10_11],
        expected: "11",
        expectedStrict: [IGNORE, "11"]
    )

    // Keep in mind that only versions in 9..13 are published
    public static final StrictPermutationsProvider SCENARIOS_TWO_DEPENDENCIES_BATCH1 = StrictPermutationsProvider.check(
        versions: [FIXED_7, FIXED_13],
        expected: "13",
        expectedStrict: [REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_12, FIXED_13],
        expected: "13",
        expectedStrictMulti: [["12", REJECTED], "13"],
        expectedStrictSingle: [REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_12, RANGE_10_11],
        expected: "12",
        expectedStrictSingle: ["12", REJECTED],
        expectedStrictMulti: ["12", [REJECTED, "11"]],
        conflicts: true
    ).and(
        versions: [FIXED_12, RANGE_10_14],
        expected: "12",
        expectedStrictSingle: ["12", "12"],
        expectedStrictMulti: ["12", ["12", "13"]]
    ).and(
        versions: [FIXED_12, RANGE_13_14],
        expected: "13",
        expectedStrictSingle: [REJECTED, "13"],
        expectedStrictMulti: [["12", REJECTED], "13"],
        conflicts: true
    ).and(
        versions: [FIXED_12, RANGE_7_8],  // No version satisfies the range [7,8]
        expected: FAILED,
        expectedStrictSingle: [FAILED, FAILED],
        expectedStrictMulti: [["12", FAILED], FAILED]
    ).and(
        versions: [FIXED_12, RANGE_14_16], // No version satisfies the range [14,16]
        expected: FAILED,
        expectedStrictSingle: [FAILED, FAILED],
        expectedStrictMulti: [["12", FAILED], FAILED],
    ).and(
        versions: [RANGE_10_11, FIXED_10],
        expected: "10",
        expectedStrictSingle: ["10", "10"],
        expectedStrictMulti: [["11", "10"], "10"],
    ).and(
        versions: [RANGE_10_14, FIXED_13],
        expected: "13",
        expectedStrict: ["13", "13"]
    ).and(
        versions: [RANGE_10_14, RANGE_10_11],
        expected: "11",
        expectedStrictSingle: ["11", "11"],
        expectedStrictMulti: [["13", "11"], "11"],
    ).and(
        versions: [RANGE_10_14, RANGE_10_16],
        expected: "13",
        expectedStrict: ["13", "13"]
    ).and(
        versions: [RANGE_10_12, RANGE_11_13], // Intersecting ranges
        expected: "12",
        expectedStrictSingle: ["12", "12"],
        expectedStrictMulti: ["12", ["12", "13"]],
    ).and(
        versions: [FIXED_12, RANGE_10_OR_HIGHER],
        expected: "12",
        expectedStrict: ["12", IGNORE]
    ).and(
        versions: [RANGE_10_11, RANGE_10_OR_HIGHER],
        expected: "11",
        expectedStrict: ["11", IGNORE]
    ).and(
        versions: [RANGE_10_14, RANGE_10_OR_HIGHER],
        expected: "13",
        expectedStrict: ["13", IGNORE]
    )

    public static final StrictPermutationsProvider SCENARIOS_TWO_DEPENDENCIES_BATCH2 = StrictPermutationsProvider.check(
        versions: [RANGE_10_OR_HIGHER, RANGE_11_OR_HIGHER],
        expected: "13"
    ).and(
        versions: [FIXED_12, RANGE_MORE_THAN_10],
        expected: "12",
        expectedStrict: ["12", IGNORE]
    ).and(
        versions: [FIXED_12, RANGE_12_OR_LOWER],
        expected: "12",
        expectedStrict: ["12", "12"]
    ).and(
        versions: [FIXED_12, RANGE_LESS_THAN_12],
        expected: "12",
        expectedStrictSingle: ["12", REJECTED],
        expectedStrictMulti: ["12", [REJECTED, "11"]],
        conflicts: true
    ).and(
        versions: [FIXED_12, RANGE_10_12_EXCLUSIVE],
        expected: "12",
        expectedStrictSingle: ["12", REJECTED],
        expectedStrictMulti: ["12", [REJECTED, "11"]],
        conflicts: true
    ).and(
        versions: [FIXED_10, RANGE_10_12_EXCLUSIVE],
        expected: "11",
        expectedStrictSingle: [REJECTED, "11"],
        expectedStrictMulti: [["10", REJECTED], "11"],
        conflicts: true
    ).and(
        versions: [RANGE_10_12, RANGE_10_12_EXCLUSIVE],
        expected: "11",
        expectedStrictSingle: ["11", "11"],
        expectedStrictMulti: [["12", "11"], "11"]
    ).and(
        versions: [RANGE_11_13, RANGE_10_12_EXCLUSIVE],
        expected: "11",
        expectedStrictSingle: ["11", "11"],
        expectedStrictMulti: [["13", "11"], "11"],
    ).and(
        versions: [RANGE_11_12, RANGE_10_12_EXCLUSIVE],
        expected: "11",
        expectedStrictSingle: ["11", "11"],
        expectedStrictMulti: [["12", "11"], "11"]
    ).and(
        versions: [DYNAMIC_PLUS, FIXED_11],
        expected: "13",
        expectedStrict: ["13", "11"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_PLUS, RANGE_10_12],
        expected: "13",
        expectedStrict: ["13", "12"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_PLUS, RANGE_10_16],
        expected: "13",
        expectedStrict: ["13", "13"]
    ).and(
        versions: [DYNAMIC_LATEST, FIXED_11],
        expected: "13",
        expectedStrict: ["13", "11"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_LATEST, RANGE_10_12],
        expected: "13",
        expectedStrict: ["13", "12"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_LATEST, RANGE_10_16],
        expected: "13",
        expectedStrict: ["13", "13"]
    )

    public static final StrictPermutationsProvider SCENARIOS_DEPENDENCY_WITH_REJECT = StrictPermutationsProvider.check(
        versions: [FIXED_12, REJECT_11],
        expected: "12"
    ).and(
        versions: [FIXED_12, REJECT_12],
        expected: REJECTED
    ).and(
        versions: [FIXED_12, REJECT_13],
        expected: "12"
    ).and(
        versions: [FIXED_10, REJECT_10_11],
        expected: REJECTED
    ).and(
        versions: [RANGE_10_12, REJECT_11],
        expected: "12"
    ).and(
        versions: [RANGE_10_12, REJECT_12],
        expected: "11"
    ).and(
        versions: [RANGE_10_12, REJECT_13],
        expected: "12"
    ).and(
        versions: [RANGE_10_12, REJECT_10_11],
        expected: "12"
    ).and(
        versions: [RANGE_10_12, REJECT_9_14],
        expected: REJECTED
    ).and(
        versions: [RANGE_10_12, REJECT_10_OR_HIGHER],
        expected: REJECTED
    ).and(
        versions: [RANGE_11_OR_HIGHER, REJECT_11],
        expected: "13"
    ).and(
        versions: [RANGE_10_OR_HIGHER, REJECT_10_11],
        expected: "13"
    ).and(
        versions: [RANGE_10_OR_HIGHER, REJECT_13],
        expected: "12"
    ).and(
        versions: [RANGE_10_OR_HIGHER, REJECT_9_14],
        expected: REJECTED
    ).and(
        versions: [RANGE_10_OR_HIGHER, REJECT_10_OR_HIGHER],
        expected: REJECTED
    ).and(
        versions: [DYNAMIC_PLUS, REJECT_11],
        expected: "13"
    ).and(
        versions: [DYNAMIC_PLUS, REJECT_13],
        expected: "12"
    ).and(
        versions: [DYNAMIC_PLUS, REJECT_10_11],
        expected: "13"
    ).and(
        versions: [DYNAMIC_PLUS, REJECT_9_14],
        expected: REJECTED
    ).and(
        versions: [DYNAMIC_PLUS, REJECT_ALL],
        expected: REJECTED
    )

    public static final StrictPermutationsProvider SCENARIOS_THREE_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_10, FIXED_12, FIXED_13],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_10, FIXED_12, RANGE_10_14],
        expected: "12",
        expectedStrict: [REJECTED, "12", "12"],
        conflicts: true
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_14],
        expected: "10",
        expectedStrict: ["10", "10", "10"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_13_14],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_10, RANGE_11_12, RANGE_10_14],
        expected: "12",
        expectedStrict: [REJECTED, "12", "12"],
        conflicts: true
    ).and(
        versions: [FIXED_11, RANGE_10_12, RANGE_11_13], // Intersecting ranges with shared fixed version
        expected: "11",
        expectedStrict: ["11", "11", "11"]
    ).and(
        ignore: "Resolution is currently order-dependent (and should not be!)",
        versions: [FIXED_11, RANGE_10_12, RANGE_12_14], // Intersecting ranges with unshared fixed version
        expected: "12",
        expectedStrict: [REJECTED, "12", "13"],
        conflicts: true
    ).and(
        versions: [FIXED_11, RANGE_10_11, FIXED_13, RANGE_10_14],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, "13", "13"],
        conflicts: true
    ).and(
        versions: [RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expected: "11",
        expectedStrict: ["11", "11", "11"]
    ).and(
        versions: [RANGE_10_11, RANGE_10_12, RANGE_13_14],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_10, FIXED_10, FIXED_12],
        expected: "12",
        expectedStrict: [REJECTED, REJECTED, "12"],
        conflicts: true
    ).and(
        versions: [FIXED_10, FIXED_12, RANGE_12_14],
        expected: "12",
        expectedStrict: [REJECTED, "12", "12"],
        conflicts: true
    )

    public static final StrictPermutationsProvider SCENARIOS_WITH_REJECT = StrictPermutationsProvider.check(
        versions: [FIXED_11, FIXED_12, REJECT_11],
        expected: "12",
        expectedStrict: [REJECTED, "12", IGNORE]
    ).and(
        versions: [FIXED_11, FIXED_12, REJECT_12],
        expected: REJECTED,
        expectedStrict: [REJECTED, REJECTED, IGNORE]
    ).and(
        versions: [FIXED_11, FIXED_12, REJECT_13],
        expected: "12",
        expectedStrict: [REJECTED, "12", IGNORE]
    ).and(
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_11],
        expected: "12",
        expectedStrict: ["12", "12", "12", IGNORE]
    ).and(
        ignore: "Will require resolving RANGE_10_14 with the knowledge that FIXED_12 rejects < '12'",
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_12],
        expected: "13",
    ).and(
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_13],
        expected: "12",
        expectedStrict: ["12", "12", "12", IGNORE]
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_11],
        expected: "13",
        expectedStrict: [REJECTED, "13", IGNORE]
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_12],
        expected: "13",
        expectedStrict: [REJECTED, "13", IGNORE]
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_13],
        expected: REJECTED,
        expectedStrict: [REJECTED, REJECTED, IGNORE]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_11],
        expected: "10",
        expectedStrict: [REJECTED, "10", "10", IGNORE]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_12],
        expected: "11",
        expectedStrict: [REJECTED, "11", "11", IGNORE]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_13],
        expected: "11",
        expectedStrict: [REJECTED, "11", "11", IGNORE]
    )

    public static final StrictPermutationsProvider SCENARIOS_FOUR_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_9, FIXED_10, FIXED_11, FIXED_12],
        expected: "12",
        expectedStrict: [REJECTED, REJECTED, REJECTED, "12"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, FIXED_12, RANGE_12_14],
        expected: "12",
        expectedStrict: [REJECTED, REJECTED, "12", "12"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_12, RANGE_13_14],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, REJECTED, "13"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expected: "10",
        expectedStrict: ["10", "10", "10", "10"]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expected: "11",
        expectedStrict: [REJECTED, "11", "11", "11"]
    )

    private static RenderableVersion empty() {
        def vs = new SimpleVersion()
        vs.version = ""
        return vs
    }

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

    private static RenderableVersion prefer(int low, int high) {
        def vs = new PreferVersion()
        vs.version = "[${low},${high}]"
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

    private static RenderableVersion reject(String version) {
        def vs = new RejectVersion()
        vs.version = version
        vs
    }

    private static RenderableVersion strict(RenderableVersion input) {
        def v = new StrictVersion()
        v.version = input.version
        v
    }

    private static RenderableVersion requireAndPrefer(int low, int high, int prefer) {
        def v = new CombinedVersion()
        v.require = "[${low},${high}]"
        v.prefer = "${prefer}"
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
            DefaultMutableVersionConstraint.withVersion(version)
        }

        @Override
        String render() {
            "'org:foo:${version}'"
        }

        @Override
        String toString() {
            return version ?: "''"
        }
    }

    static class StrictVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            DefaultMutableVersionConstraint.withStrictVersion(version)
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
            def vc = new DefaultMutableVersionConstraint('')
            vc.prefer(version)
            return vc
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

    static class CombinedVersion implements RenderableVersion {
        String require
        String prefer

        @Override
        VersionConstraint getVersionConstraint() {
            def vc = new DefaultMutableVersionConstraint('')
            vc.prefer(prefer)
            vc.require(require)
            return vc
        }

        @Override
        String render() {
            return "('org:foo') { version { prefer '${prefer}'; require '${require}' }"
        }

        @Override
        String toString() {
            return "require(" + require + ") prefer(" + prefer + ")"
        }

        @Override
        String getVersion() {
            return require + "/" + prefer
        }
    }

    static class RejectVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            DefaultImmutableVersionConstraint.of("", "", "", [version])
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
            assert config.versions != null
            assert config.expected != null

            ++batchCount
            if (!config.ignore) {
                List<RenderableVersion> versions = config.versions
                String expected = config.expected
                List<String> expectedStrictSingle = config.expectedStrict?:config.expectedStrictSingle
                List<String> expectedStrictMulti = config.expectedStrict?:config.expectedStrictMulti
                boolean expectConflict = config.conflicts as boolean
                List<Batch> iterations = []
                String batchName = config.description ?: "#${batchCount} (${versions})"
                iterations.add(new Batch(batchName, versions, expected, expected, expectConflict))
                if (expectedStrictSingle || expectedStrictMulti) {
                    versions.size().times { idx ->
                        def expectedStrictResolution = expectedStrictMulti[idx]
                        if (expectedStrictResolution != IGNORE) {
                            iterations.add(new Batch(batchName, versions.withIndex().collect { RenderableVersion version, idx2 ->
                                if (idx == idx2) {
                                    strict(version)
                                } else {
                                    version
                                }
                            }, expectedStrictSingle[idx], expectedStrictMulti[idx], expectConflict))
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
            Object expectedSingle
            Object expectedMulti
            boolean expectConflict
        }

        class PermutationIterator implements Iterator<Candidate> {
            Iterator<Batch> batchesIterator = batches.iterator()
            String currentBatch
            Iterator<List<RenderableVersion>> current
            Object expectedSingle
            Object expectedMulti
            boolean expectConflictResolution
            int idx = 0

            @Override
            boolean hasNext() {
                batchesIterator.hasNext() || current?.hasNext()
            }

            @Override
            Candidate next() {
                if (current?.hasNext()) {
                    if (expectedSingle instanceof Iterable) {
                        expectedSingle = expectedSingle.iterator()
                    }
                    if (expectedMulti instanceof Iterable) {
                        expectedMulti = expectedMulti.iterator()
                    }
                    Object nextExpectedSingle = expectedSingle instanceof String ? expectedSingle : expectedSingle.next()
                    Object nextExpectedMulti = expectedMulti instanceof String ? expectedMulti : expectedMulti.next()
                    def candidate = new Candidate(idx: idx++, batch: currentBatch, candidates: current.next() as RenderableVersion[], expectedSingle: nextExpectedSingle, expectedMulti:nextExpectedMulti, conflicts: expectConflictResolution)
                    return candidate
                }
                Batch nextBatch = batchesIterator.next()
                expectedSingle = nextBatch.expectedSingle
                expectedMulti = nextBatch.expectedMulti
                expectConflictResolution = nextBatch.expectConflict
                current = permutations(nextBatch).iterator()
                if (nextBatch.batchName != currentBatch) {
                    idx = 0
                }
                currentBatch = nextBatch.batchName
                return next()
            }

            // an alternative to Groovy's permutations method which is reproducible
            private static List<List<RenderableVersion>> permutations(Batch nextBatch) {
                def generator = new PermutationGenerator<>(nextBatch.versions)
                def permutations = []
                def permutationsAsSet = [] as Set
                while (generator.hasNext()) {
                    def next = generator.next()
                    if (permutationsAsSet.add(next)) {
                        permutations << next
                    }
                }
                permutations
            }
        }

        static class Candidate {
            int idx
            String batch
            RenderableVersion[] candidates
            String expectedSingle
            String expectedMulti
            boolean conflicts

            @Override
            String toString() {
                String expected = expectedSingle == expectedMulti ? expectedSingle : "single:${expectedSingle} / multi:${expectedMulti}"
                batch + ": $idx - " + candidates.collect { it.toString() }.join(' & ') + " -> $expected"
            }
        }
    }
}
