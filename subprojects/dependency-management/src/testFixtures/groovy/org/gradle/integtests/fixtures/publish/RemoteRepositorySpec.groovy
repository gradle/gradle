/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.publish

import org.gradle.test.fixtures.HttpRepository

class RemoteRepositorySpec {
    public static final ThreadLocal<Boolean> DEFINES_INTERACTIONS = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            false
        }
    }

    final Map<String, GroupSpec> groups = [:].withDefault { new GroupSpec(it) }

    void nextStep() {
        groups.clear()
    }

    void group(String group, @DelegatesTo(value=GroupSpec, strategy = Closure.DELEGATE_ONLY) Closure<Void> groupSpec) {
        groupSpec.delegate = groups[group]
        groupSpec.resolveStrategy = Closure.DELEGATE_ONLY
        groupSpec()
    }

    void build(HttpRepository repository) {
        groups.values()*.build(repository)
    }

    /**
     * Defines modules using a simple path notation. Assuming group is "org", and default version is 1.0
     * For example: foo -> bar
     * would create 'org:foo:1.0' depending on 'org:bar:1.0'
     * when: foo -> bar:2.0 -> baz
     * would create 'org:foo:1.0' depending on 'org:bar:2.0' depending on `baz:1.0'
     * @param pathSpec
     */
    void path(String pathSpec) {
        def pathElements = (pathSpec.split("\\s?->\\s?") as List<String>).reverse()
        def last = null
        pathElements.each { String spec ->
            def gav = spec.split(':') as List<String>
            if (gav.size()==1) {
                // name only
                gav = ["org", gav[0], "1.0"]
            } else if (gav.size() == 2) {
                // name, version
                gav = ["org", *gav]
            }
            def (g, a, v) = gav
            group(g) {
                module(a) {
                    version(v) {
                        if (last) {
                            dependsOn(last)
                        }
                    }
                }
            }
            last = "$g:$a:$v"
        }
    }

    def id(String notation, @DelegatesTo(value=ModuleVersionSpec, strategy = Closure.DELEGATE_ONLY) Closure spec = {}) {
        def (gid, aid, v) = notation.split(':') as List
        assert gid && aid && v
        group(gid) {
            module(aid) {
                version(v, spec)
            }
        }
    }

    /**
     * Use {@link #id} when possible since it doesn't rely on {@code methodMissing} and integrates better with IDEs.
     */
    void methodMissing(String name, args) {
        def (gid, aid, v) = name.split(':') as List
        Closure spec = {}
        if (args && args.length == 1 && args[0] instanceof Closure) {
            spec = args[0]
        }
        if (gid && aid) {
            group(gid) {
                if (v) {
                    module(aid) {
                        version(v, spec)
                    }
                } else {
                    module(aid, spec)
                }
            }
        } else if (gid) {
            group(gid, spec)
        }

    }
}
