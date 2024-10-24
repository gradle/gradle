package org.gradle.internal.cc.impl.isolated

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class IsolatedProjectsAbsoluteTaskRequestConfigurationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(5_000)

    def setup() {
        server.start()
    }

    def 'not so humble beginnings'() {
        given:
        settingsFile '''
            include "a"
            include "b"
            include "c"
        '''

        ["a", "b", "c"].each {
            buildFile file("$it/build.gradle"), """
                ${server.callFromBuildUsingExpression("name")}
                tasks.register('build') {
                    doLast {
                        println path
                    }
                }
            """
        }

        and:
        server.expectConcurrent("a", "c")

        when: // gets complicated because of task options (i.e. Gradle cannot tell which arguments are tasks and which are options)
        isolatedProjectsRun ":a:build", ":c:build"

        then:
        result.assertTasksExecuted(":a:build", ":c:build")
    }

    def 'humble beginnings'() {
        given:
        settingsFile '''
            include "a"
            include "b"
            include "c"
        '''

        buildFile '''
            tasks.register('build') {
                dependsOn(":a:build")
                dependsOn(":c:build")
            }
        '''

        ["a", "b", "c"].each {
            buildFile file("$it/build.gradle"), """
                ${server.callFromBuildUsingExpression("name")}
                tasks.register('build') {
                    doLast {
                        println path
                    }
                }
            """
        }

        and:
        server.expectConcurrent("a", "c")
//        server.expect("c")
//        server.expect("a")

        when:
        isolatedProjectsRun ":build"

        then:
        result.assertTasksExecuted(":build", ":a:build", ":c:build")
    }
}
