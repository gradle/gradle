/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.properties.annotations

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty
import org.gradle.util.TestUtil
import org.jspecify.annotations.Nullable
import spock.lang.Specification

class LazyPropertyPathResolverTest extends Specification {

    def "resolves a nested path"() {
        def owner = new Owner()

        expect:
        LazyPropertyPathResolver.resolve(owner, "message", "lazyProperties.message").is(owner.lazyProperties.message)
        LazyPropertyPathResolver.resolve(owner, "files", "lazyProperties.files").is(owner.lazyProperties.files)
    }

    def "resolves a protected getter on the same object"() {
        def owner = new Owner()

        expect:
        LazyPropertyPathResolver.resolve(owner, "flag", "flagProperty").is(owner.flagProperty)
    }

    def "finds boolean is-getters"() {
        expect:
        LazyPropertyPathResolver.findStaticProblem(Owner, "enabled") == "the path ends at type 'boolean', which is not a Provider or a configurable value"
    }

    def "uses the narrowed type of a covariant override"() {
        def owner = new SubOwner()

        expect:
        LazyPropertyPathResolver.resolve(owner, "extra", "lazyProperties.extra").is(owner.lazyProperties.extra)
        LazyPropertyPathResolver.findStaticProblem(SubOwner, "lazyProperties.extra") == null
        LazyPropertyPathResolver.findStaticProblem(Owner, "lazyProperties.extra") == "segment 'extra' has no getter getExtra() or isExtra() on type '${Inputs.name}'"
    }

    def "reports a missing getter with owner, getter and segment"() {
        when:
        LazyPropertyPathResolver.resolve(new Owner(), "message", "lazyProperties.missing")

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Cannot resolve @BackedByLazyProperty(\"lazyProperties.missing\") of ${Owner.name}.getMessage(): segment 'missing' has no getter getMissing() or isMissing() on type '${Inputs.name}'."
    }

    def "reports a null segment"() {
        def owner = new Owner()
        owner.lazyProperties = null

        when:
        LazyPropertyPathResolver.resolve(owner, "message", "lazyProperties.message")

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Cannot resolve @BackedByLazyProperty(\"lazyProperties.message\") of ${Owner.name}.getMessage(): segment 'lazyProperties' is null."
    }

    def "returns null for a null segment whose getter is declared nullable"() {
        expect:
        LazyPropertyPathResolver.resolve(new UnattachedOwner(), "message", "lazyProperties.message") == null
    }

    def "reports a path that does not end at a lazy type"() {
        when:
        LazyPropertyPathResolver.resolve(new Owner(), "message", "lazyProperties")

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Cannot resolve @BackedByLazyProperty(\"lazyProperties\") of ${Owner.name}.getMessage(): the path ends at type '${Inputs.name}', which is not a Provider or a configurable value."
    }

    def "reports an empty segment"() {
        expect:
        LazyPropertyPathResolver.findStaticProblem(Owner, path) == "the path has an empty segment"

        where:
        path << ["", "lazyProperties.", ".lazyProperties", "lazyProperties..message"]
    }

    def "validates paths on declared types"() {
        expect:
        LazyPropertyPathResolver.findStaticProblem(Owner, path) == problem

        where:
        path                     | problem
        "lazyProperties.message" | null
        "lazyProperties.files"   | null
        "flagProperty"           | null
        "lazyProperties.missing" | "segment 'missing' has no getter getMissing() or isMissing() on type '${Inputs.name}'"
        "message"                | "the path ends at type 'java.lang.String', which is not a Provider or a configurable value"
    }

    def "finds the backing path of a getter across the class hierarchy"() {
        expect:
        LazyPropertyPathResolver.findBackingPath(SubOwner, "message") == "lazyProperties.message"
        LazyPropertyPathResolver.findBackingPath(SubOwner, "extra") == "lazyProperties.extra"
        LazyPropertyPathResolver.findBackingPath(SubOwner, "flag") == "flagProperty"
        LazyPropertyPathResolver.findBackingPath(Owner, "extra") == null
        LazyPropertyPathResolver.findBackingPath(Owner, "unknown") == null
    }

    static class Inputs {
        final Property<String> message = TestUtil.objectFactory().property(String)
        final ConfigurableFileCollection files = TestUtil.objectFactory().fileCollection()
    }

    static class SubInputs extends Inputs {
        final Property<String> extra = TestUtil.objectFactory().property(String)
    }

    static class Owner {
        Inputs lazyProperties = new Inputs()
        private final Property<Boolean> flag = TestUtil.objectFactory().property(Boolean)

        Inputs getLazyProperties() {
            return lazyProperties
        }

        @BackedByLazyProperty("lazyProperties.message")
        String getMessage() {
            return lazyProperties.message.getOrNull()
        }

        @BackedByLazyProperty("flagProperty")
        boolean isFlag() {
            return flag.get()
        }

        boolean isEnabled() {
            return true
        }

        protected Property<Boolean> getFlagProperty() {
            return flag
        }
    }

    static class UnattachedOwner {
        @Nullable
        private Inputs getLazyProperties() {
            return null
        }

        @BackedByLazyProperty("lazyProperties.message")
        String getMessage() {
            return null
        }
    }

    static class SubOwner extends Owner {
        private final SubInputs subInputs = new SubInputs()

        @Override
        SubInputs getLazyProperties() {
            return subInputs
        }

        @Override
        String getMessage() {
            return super.getMessage()
        }

        @BackedByLazyProperty("lazyProperties.extra")
        String getExtra() {
            return subInputs.extra.getOrNull()
        }
    }
}
