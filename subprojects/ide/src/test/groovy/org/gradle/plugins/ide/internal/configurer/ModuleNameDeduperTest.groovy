/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.internal.configurer

import org.gradle.api.Project
import spock.lang.Specification

class ModuleNameDeduperTest extends Specification {

    public static class TargetStub extends DeduplicationTarget {
        TargetStub(Project project) {
            this.moduleName = project.name
            this.project = project
        }
        Closure updateModuleName = { moduleName = it }
    }

    def projectMock(String projectName, Project parent = null) {
        Project project = Mock()
        _ * project.name >> projectName
        _ * project.parent >> parent
        project
    }


    ModuleNameDeduper deduper = new ModuleNameDeduper()

    TargetStub root = new TargetStub(projectMock("myproject"))
    TargetStub sub1 = new TargetStub(projectMock("sub1", root.project))
    TargetStub sub2 = new TargetStub(projectMock("sub2", root.project))
    TargetStub sub1App = new TargetStub(projectMock("app", sub1.project))
    TargetStub sub2App = new TargetStub(projectMock("app", sub2.project))


    def "does nothing when no duplicates"() {
        given:
        def dedupeMe = [sub1, sub2]

        when:
        deduper.dedupe(dedupeMe)

        then:
        dedupeMe == [sub1, sub2]
        sub1.moduleName == "sub1"
        sub2.moduleName == "sub2"
    }

    def "root project is not deduplicated"() {
        given:
        TargetStub sub = new TargetStub(projectMock("myproject", root.project))
        def dedupeMe = [root, sub]

        when:
        deduper.dedupe(dedupeMe)

        then:
        dedupeMe == [root, sub]
        root.moduleName == "myproject"
        sub.moduleName == "myproject-myproject"
    }

    def "keeps similar parent and child project name"() {
        def similarSub = new TargetStub(projectMock("myproject", root.project))
        given:
        def dedupeMe = [root, sub1, similarSub ]

        when:
        deduper.dedupe(dedupeMe)

        then:
        dedupeMe == [root, sub1, similarSub]
        root.moduleName == "myproject"
        sub1.moduleName == "sub1"
        similarSub.moduleName == "myproject-myproject"
    }

    def "should dedup module names"() {
        given:
        def dedupeMe = [root, sub1, sub2, sub1App, sub2App]
        assert sub1App.moduleName == "app"
        assert sub1App.moduleName == "app"

        when:
        deduper.dedupe(dedupeMe)

        then:
        sub1App.moduleName == "sub1-app"
        sub2App.moduleName == "sub2-app"
    }

    def "removes duplicate words from project name"() {
        given:
        TargetStub myProjectApp1 = new TargetStub(projectMock("myproject-app", root.project))
        TargetStub myProjectBar1 = new TargetStub(projectMock("myproject-bar", root.project))
        TargetStub myProjectApp2 = new TargetStub(projectMock("myproject-app", myProjectBar1.project))

        def dedupeMe = [root, myProjectApp1, myProjectBar1, myProjectApp2]
        assert myProjectApp1.moduleName == "myproject-app"
        assert myProjectBar1.moduleName == "myproject-bar"
        assert myProjectApp2.moduleName == "myproject-app"

        when:
        deduper.dedupe(dedupeMe)

        then:
        myProjectApp1.moduleName == "myproject-app"
        myProjectBar1.moduleName == "myproject-bar"
        myProjectApp2.moduleName == "myproject-bar-app"
    }

    def "allows deduplication with parent not part of the target list"() {
        given:
        TargetStub bar = new TargetStub(projectMock("bar", root.project))
        TargetStub barApp = new TargetStub(projectMock("app", bar.project))
        TargetStub foo = new TargetStub(projectMock("foo", root.project))
        TargetStub fooApp = new TargetStub(projectMock("app", foo.project))

        when:
        deduper.dedupe([barApp, fooApp])

        then:
        barApp.moduleName == "bar-app"
        fooApp.moduleName == "foo-app"
    }

    def "should use deduped parent module name for deduping"() {
        given:
        TargetStub bar = new TargetStub(projectMock("bar", root.project))
        TargetStub barServices = new TargetStub(projectMock("services", bar.project))
        TargetStub barServicesApp = new TargetStub(projectMock("app", barServices.project))
        TargetStub foo = new TargetStub(projectMock("foo", root.project))
        TargetStub fooServices = new TargetStub(projectMock("services", foo.project))
        TargetStub fooServicesApp = new TargetStub(projectMock("app", fooServices.project))
        def dedupeMe = [barServices, barServicesApp, fooServices, fooServicesApp]

        when:
        deduper.dedupe(dedupeMe)

        then:
        barServices.moduleName == "bar-services"
        fooServices.moduleName == "foo-services"
        fooServicesApp.moduleName == "foo-services-app"
        barServicesApp.moduleName == "bar-services-app"
    }
}
