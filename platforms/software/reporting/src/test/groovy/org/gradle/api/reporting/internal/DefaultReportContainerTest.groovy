/*
 * Copyright 2009 the original author or authors.
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


package org.gradle.api.reporting.internal

import org.gradle.api.Describable
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.provider.Property
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportContainer
import org.gradle.internal.Describables
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultReportContainerTest extends Specification {
    static class TestReportContainer extends DefaultReportContainer {
        TestReportContainer(Closure c) {
            super(Report, TestUtil.instantiatorFactory().decorateLenient(), CollectionCallbackActionDecorator.NOOP)

            c.delegate = new Object() {
                Report createReport(String name) {
                    add(TestReport, name, Describables.of(name), Report.OutputType.FILE)
                }
            }

            c()
        }
    }

    DefaultReportContainer createContainer(Closure cl) {
        try {
            TestUtil.instantiatorFactory().decorateLenient().newInstance(TestReportContainer, cl)
        } catch (ObjectInstantiationException e) {
            throw e.cause
        }
    }

    DefaultReportContainer container

    def setup() {
        container = createContainer {
            createReport("a")
            createReport("b")
            createReport("c")
        }
    }

    def "reports given at construction are available"() {
        when:
        container.configure { a {} }

        then:
        notThrown(MissingPropertyException)
    }

    def "container is immutable"() {
        when:
        container.add(Stub(Report))

        then:
        thrown(ReportContainer.ImmutableViolationException)

        when:
        container.clear()

        then:
        thrown(ReportContainer.ImmutableViolationException)
    }

    def "require empty by default"() {
        expect:
        container.every { !it.required.get() } && container.enabled.empty
    }

    def "can change required"() {
        when:
        container.each { it.required = false }

        then:
        container.enabled.empty

        when:
        container.configure {
            a.required = true
            b.required = true
        }

        then:
        container.enabled.size() == 2
    }

    def "cannot add report named 'enabled'"() {
        when:
        createContainer {
            createReport "enabled"
        }

        then:
        thrown(InvalidUserDataException)
    }

    def "cant access or configure non existent report"() {
        when:
        container.configure {
            dontexist {

            }
        }

        then:
        thrown(MissingMethodException)
    }

    static class TestReport extends SimpleReport {
        final Property<Boolean> required = TestUtil.objectFactory().property(Boolean).value(false)

        TestReport(String name, Describable displayName, OutputType outputType) {
            super(name, displayName, outputType)
        }

        @Override
        FileSystemLocationProperty<? extends FileSystemLocation> getOutputLocation() {
            return null
        }
    }
}
