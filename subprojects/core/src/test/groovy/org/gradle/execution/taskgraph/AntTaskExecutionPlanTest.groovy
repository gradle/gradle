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

package org.gradle.execution.taskgraph

import org.gradle.api.internal.project.DefaultAntBuilder
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.initialization.BuildCancellationToken
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue

import java.util.zip.GZIPInputStream

import static org.gradle.util.TestUtil.createRootProject

class AntTaskExecutionPlanTest extends AbstractProjectBuilderSpec {

    DefaultTaskExecutionPlan executionPlan
    ProjectInternal root
    def cancellationHandler = Mock(BuildCancellationToken)
    private final AntLoggingAdapter loggingAdapter = Mock(AntLoggingAdapter)
    def ant

    def setup() {
        root = createRootProject(temporaryFolder.testDirectory)
        executionPlan = new DefaultTaskExecutionPlan(cancellationHandler)
        ant = new DefaultAntBuilder(project, loggingAdapter)
    }

    private void addToGraphAndPopulate(List tasks) {
        executionPlan.addToTaskGraph(tasks)
        executionPlan.determineExecutionPlan()
    }

    // The test case is imported verbatim from the issue's attachment.
    @Issue("https://github.com/gradle/gradle/issues/2293")
    def "determining execution plan for an imported ant project should not fail on empty cycles list"() {
        File buildFile = new File(project.projectDir, 'build.xml')
        buildFile.withOutputStream { OutputStream os ->
            def gzippedFile =
                '''
                H4sIANSWYFkAA+1Z25KiMBB99ysofJ1FLiFAlfo0T1v7EzG2ihsCRcJW+fcbRHdm
                HC8kRldn9wUL0pf0Sfqk046rulwDlQ4nBUzcRTMjtevMYUEaJifurMnZ3J0OBmNJ
                6iXsxbrPSqwCPhcTl5ZFlTP4Tn4RJew4Y6Cr0ilACLJU4q8lB891RtPBeNTZ+WSR
                CAHFjEEn9WFoAZKuXreugNMchPtJ5L3/t1mt1au3G/LWIGc1ybnwikp4rFzm9MW5
                JDHEOMRJFEZ+FsQ+CnCWxpe1vLrhMi+gjyQQKsu6h6QEIc+LEb705KYCsRESilBL
                eIjiJM1CFWqKEz+JsiyKDg20L0U5bxgIj26o+hRo+egHy6GSelSMSE2tFbAKLsDa
                6ggVELBOVU96mEQoDuPEx0mcJjgIAoT05iiIigwq1ixzbqJJV0B/Qu2tCJ8z9dvI
                nJ2105rYKuXKHuG8lETm5XnfJ3SGKU5xjLNMPf0QoxT5qUkMP9RnU71hiiKcxRFK
                fYSCyI+Qn5nYMvWvn5I7ZWPF/Yq3+luiPKDBLju/tcPiHQ/uufXFaUe22rTki3x5
                hGwv8ZAGvWqQ2x9m6MH1Jpxt9VB4i8vCbPehG+FqxX9n7J1/G1a7HXoT0O+2mQ5y
                sGc4Vg49Q4I3PDyfZQWOFSlmq3KHuuWfXcIjZaJh6vQt4mzA/hg7XPM0eEbAetwp
                LGO6zyBDUL8+QN22sVcBHF6VnuHktFpY9bkraoCid0n9OhCeuiz/Z0frwB6L8KGv
                eif6EtZITKPv0Q+nk82Xm6xn2594Bto1r3YfoFg734q6qtzo+mI3ma+tmHXu+KbN
                Pzs0erKbfhN8r8Hlitz7O/nav4f9UDn7sZF676UybXrfc7kejpiPta4H49Hu78vp
                4Dc8qG0KyRwAAA==
                '''.decodeBase64()
            def is = new GZIPInputStream(new ByteArrayInputStream(gzippedFile))
            int c
            while ((c = is.read()) >= 0) {
                os.write(c)
            }
        }

        when:
        ant.importBuild(buildFile)
        addToGraphAndPopulate(project.getAllTasks(true)[project].asList())

        then:
        notThrown(IndexOutOfBoundsException)
    }

}
