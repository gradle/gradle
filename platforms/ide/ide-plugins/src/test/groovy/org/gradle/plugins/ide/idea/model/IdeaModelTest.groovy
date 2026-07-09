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

package org.gradle.plugins.ide.idea.model

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.util.TestUtil
import spock.lang.Specification

class IdeaModelTest extends Specification {

    IdeaModel model = TestUtil.newInstance(IdeaModel)

    def "can configure project with Actions"() {
        given:
        def gradleProject = Stub(ProjectInternal) {
            getObjects() >> TestUtil.objectFactory()
        }
        model.project = TestUtil.newInstance(IdeaProject, gradleProject)

        when: "configure project"
        model.project({ p -> p.vcs = 'GIT' } as Action<IdeaProject>)

        then:
        model.project.vcs == 'GIT'
    }

    def "can configure module with Actions"() {
        given:
        def project = Mock(org.gradle.api.Project) {
            def objectFactory = Mock(ObjectFactory)
            def fileCollection = Mock(ConfigurableFileCollection)
            objectFactory.fileCollection() >> fileCollection

            getObjects() >> objectFactory
            provider(_) >> Mock(Provider)
            getProjectDir() >> new File("root")
        }
        model.module = TestUtil.newInstance(IdeaModule, project)

        when: "configure module"
        model.module({ mod -> mod.name = 'name' } as Action<IdeaModule>)

        then:
        model.module.name == 'name'
    }
}
