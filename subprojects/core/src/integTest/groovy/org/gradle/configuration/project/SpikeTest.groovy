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

package org.gradle.configuration.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.configuration.LifecycleListenerExecutionBuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord

class SpikeTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name='root'"
    }

    def test() {
        given:
        // set of hopefully complete registration points
        settingsFile << """
gradle.with {
	rootProject {
	    println "rootProject"
		beforeEvaluate {
			println "settings gradle.rootProject.beforeEvaluate (Closure)"
		}
		beforeEvaluate({
			println "settings gradle.rootProject.beforeEvaluate (Action)"
		} as Action)
			
		afterEvaluate {
			println "settings gradle.rootProject.afterEvaluate (Closure)"
		}
		afterEvaluate({
			println "settings gradle.rootProject.afterEvaluate (Action)"
		} as Action)
	}

	beforeProject {
		println "settings gradle.beforeProject (Closure) \$it"
	}
	beforeProject({
		println "settings gradle.beforeProject (Action) \$it"
	} as Action)

	afterProject {
		println "settings gradle.afterProject (Closure) \$it"
	}
	afterProject({
		println "settings gradle.afterProject (Action) \$it"
	} as Action)

	projectsLoaded {
		println "settings gradle.projectsLoaded (Closure)"
	}
	projectsLoaded({
		println "settings gradle.projectsLoaded (Action)"
	} as Action)

	projectsEvaluated {
		println "settings gradle.projectsEvaluated (Closure)"
	}
	projectsEvaluated({
		println "settings gradle.projectsEvaluated (Action)"
	} as Action)

	addProjectEvaluationListener(new ProjectEvaluationListener() {
		void beforeEvaluate(Project project) {
			println "settings gradle.addProjectEvaluationListener.beforeEvaluate \$project"
		}
		void afterEvaluate(Project project, ProjectState state) {
			println "settings gradle.addProjectEvaluationListener.afterEvaluate \$project"
		}
	})
	addListener(new ProjectEvaluationListener() {
		void beforeEvaluate(Project project) {
			println "settings gradle.addListener(ProjectEvaluationListener).beforeEvaluate \$project"
		}
		void afterEvaluate(Project project, ProjectState state) {
			println "settings gradle.addListener(ProjectEvaluationListener).afterEvaluate \$project"
		}
	})


	addBuildListener(new BuildAdapter() {
		 void projectsLoaded(Gradle gradle) {
 			println "settings gradle.addBuildListener.projectsLoaded"		 	
		 }
		 void projectsEvaluated(Gradle gradle) {
 			println "settings gradle.addBuildListener.projectsEvaluated"		 	
		 }
	})

	addListener(new BuildAdapter() {
		 void projectsLoaded(Gradle gradle) {
 			println "settings gradle.addListener(BuildListener).projectsLoaded"		 	
		 }
		 void projectsEvaluated(Gradle gradle) {
 			println "settings gradle.addListener(BuildListener).projectsEvaluated"		 	
		 }
	})
	
	taskGraph.with {
		whenReady {
			println "settings gradle.taskGraph.whenReady (Closure)"
		}
		whenReady({
			println "settings gradle.taskGraph.whenReady (Action)"
		} as Action)
		
		addTaskExecutionGraphListener({
			println "settings gradle.taskGraph.addTaskExecutionGraphListener.graphPopulated"
		} as TaskExecutionGraphListener)
	}

	addListener({
		println "settings gradle.addListener(TaskExecutionGraphListener).graphPopulated"
	} as TaskExecutionGraphListener)
	
}"""
        // and one in build.gradle, just to test parent op creation from a project script, and nested afterEvaluates
        buildFile << """
            println 'build.gradle'
            afterEvaluate {
                println "build.gradle afterEvaluate (Closure)"
                
                afterEvaluate {
                    println "build.gradle afterEvaluate afterEvaluate (Closure)"
                }
            }
        """

        when:
        succeeds 'help'
        operations.all(~/.*/).each { println "$it (id: $it.id, parent: $it.parentId)"; it.progress.each { if (it.details.containsKey('spans')) print "  ${it.details.spans.text.join("")}" } }

        then:
        def afterEvaluateWrapperOpId = operations.only(NotifyProjectAfterEvaluatedBuildOperationType).id
        def afterEvaluateOps = operations.all(LifecycleListenerExecutionBuildOperationType) // we only have afterEvaluate decorated currently
        afterEvaluateOps.size() == 8 // 6 in settings.gradle, 2 in build.gradle
        // these all belong to the expected parent
        afterEvaluateOps.each {
            it.parentId == afterEvaluateWrapperOpId
        }
        // nothing else directly under the wrapper op
        operations.all(~/.*/).findAll { it.parentId == afterEvaluateWrapperOpId }.size() == afterEvaluateOps.size()

        // did we capture the correct parent op id at time of registration in these ops?
        def settingsScriptOpId = operations.only(~/Apply script settings.gradle to settings '.*'/).id
        def crossConfigureOpId = operations.only("Cross-configure project :").id
        def buildScriptOpId = operations.only("Apply script build.gradle to root project 'root'").id
        afterEvaluateOps.find { hasProgress(it, "settings gradle.addProjectEvaluationListener.afterEvaluate root project 'root'\n") }.details.applicationId == settingsScriptOpId
        afterEvaluateOps.find { hasProgress(it, "settings gradle.rootProject.afterEvaluate (Action)\n") }.details.applicationId == crossConfigureOpId
        afterEvaluateOps.find { hasProgress(it, "build.gradle afterEvaluate (Closure)\n") }.details.applicationId == buildScriptOpId

        // find nested one, should have the script as the parent, not the enclosing listener
        // NOTE: this does not work currently - may be better to stitch these together in the consumer, which should be observing them all anyway
        //afterEvaluateOps.find { hasProgress(it, "build.gradle afterEvaluate afterEvaluate (Closure)\n") }.details.applicationId == buildScriptOpId
    }

    private static boolean hasProgress(BuildOperationRecord record, String msg) {
        record.progress.find { it.details.containsKey('spans') && it.details.spans.text.join("") == msg } != null
    }
}
