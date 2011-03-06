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
package org.gradle.plugins.idea

import org.gradle.plugins.idea.configurer.DefaultIdeaAssetsConfigurer
import org.gradle.plugins.idea.configurer.ModuleNameDeduper
import spock.lang.Specification

/**
 * @author Szczepan Faber, @date 06.03.11
 */
class DefaultIdeaAssetsConfigurerTest extends Specification {

    static class ProjectStub {
        def String path
        def Object ideaModule
    }

    DefaultIdeaAssetsConfigurer configurer = new DefaultIdeaAssetsConfigurer()

    def "should sort and get pass modules to deduper"() {
        given:
        def master = new ProjectStub(path: ":master", ideaModule: "master module")
        def child = new ProjectStub(path: ":master:child", ideaModule: "child module")
        def projectsChildFirst = [child, master]
        configurer.deduper = Mock(ModuleNameDeduper)

        when:
        configurer.configure(projectsChildFirst)

        then:
        1 * configurer.deduper.dedupeModuleNames(['master module', 'child module'])
    }
}
