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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Specification

class DefaultCompositeExcludeTest extends Specification {
    private static final DefaultExcludeFactory FACTORY = new DefaultExcludeFactory()
    private static final String[] GROUPS = ["org.foo", "org.bar", "org.baz", "com.acme"]
    private static final String[] MODULES = ["mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune"]
    private static final IvyArtifactName[] ARTIFACTS = [artifactName("foo"), artifactName('bar'), artifactName('baz'), artifactName('foo', 'jar', 'jar', 'classy')]
    private static final Long SEED = Long.getLong("org.gradle.internal.test.excludes.seed", 58745094L)
    private static final int MAX_DEPTH = 3
    private static final int MAX_CACHED = 10_000

    private final Random random = new Random(SEED)
    private final Set<ExcludeSpec> cached = new HashSet<>()
    private final List<ExcludeSpec> cachedLinear = new ArrayList<>(MAX_CACHED)
    private int depth

    /**
     * This test has been baked to maximize code coverage of {@link DefaultCompositeExclude}
     */
    def "compares specs"() {
        expect:
        (500_000).times {
            ExcludeSpec a = next()
            ExcludeSpec b = next()

            if (a == b) {
                assert b == a
            } else {
                assert b != a
            }
        }
        true
    }

    ExcludeSpec cache(ExcludeSpec spec) {
        if (cached.add(spec)) {
            cachedLinear << spec
        }
        spec
    }

    ExcludeSpec next() {
        try {
            int rnd = random.nextInt(9)
            if (depth++ == MAX_DEPTH) {
                // avoid too deep levels
                rnd = rnd % 6
            }
            if (cached.size() >= MAX_CACHED) {
                rnd = 0 // always use a cached value
            }
            switch (rnd) {
                case 0:
                    return cached ? cachedLinear[random.nextInt(cached.size())] : next()
                case 1:
                    return cache(nextModule())
                case 2:
                    return cache(nextGroup())
                case 3:
                    return cache(nextModuleId())
                case 4:
                    return cache(nextGroupSet())
                case 5:
                    return cache(nextModuleIdSet())
                case 6:
                    return cache(nextArtifact())
                case 7:
                    return cache(nextAny())
                case 8:
                    return cache(nextAll())
            }
            throw new IllegalStateException()
        } finally {
            depth--
        }
    }

    ModuleExclude nextModule() {
        FACTORY.module(randomModuleName())
    }

    GroupExclude nextGroup() {
        FACTORY.group(randomGroupName())
    }

    ModuleIdExclude nextModuleId() {
        FACTORY.moduleId(randomModuleId())
    }

    ModuleSetExclude nextModuleSet() {
        FACTORY.moduleSet((0..(1 + random.nextInt(5))).collect { randomModuleName() } as Set<String>)
    }

    GroupSetExclude nextGroupSet() {
        FACTORY.groupSet((0..(1 + random.nextInt(5))).collect { randomGroupName() } as Set<String>)
    }

    ModuleIdSetExclude nextModuleIdSet() {
        FACTORY.moduleIdSet((0..(1 + random.nextInt(5))).collect { randomModuleId() } as Set<ModuleIdentifier>)
    }

    ExcludeSpec nextAny() {
        FACTORY.anyOf((0..(1 + random.nextInt(3))).collect { next() } as Set<ExcludeSpec>)
    }

    ExcludeSpec nextAll() {
        FACTORY.allOf((0..(1 + random.nextInt(3))).collect { next() } as Set<ExcludeSpec>)
    }

    ExcludeSpec nextArtifact() {
        FACTORY.ivyPatternExclude(randomModuleId(), randomArtifactName(), "*")
    }

    private String randomGroupName() {
        GROUPS[random.nextInt(GROUPS.length)]
    }

    private String randomModuleName() {
        MODULES[random.nextInt(MODULES.length)]
    }

    private ModuleIdentifier randomModuleId() {
        DefaultModuleIdentifier.newId(randomGroupName(), randomModuleName())
    }

    private IvyArtifactName randomArtifactName() {
        ARTIFACTS[random.nextInt(ARTIFACTS.length)]
    }

    private static IvyArtifactName artifactName(String name, String type = 'jar', String extension = 'jar', String classifier = null) {
        new DefaultIvyArtifactName(name, type, extension, classifier)
    }
}
