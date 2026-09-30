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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

/**
 * An output property of an object that is nested in a task does not always know which task declares it.
 * The task is known when the property is queried using the provider of the task.
 */
@Issue("https://github.com/gradle/gradle/issues/6619")
class NestedOutputDependencyInferenceIntegrationTest extends AbstractIntegrationSpec {

    private static final String NO_TASK = "Property 'destination' is declared as an output property of an object with type Report but does not have a task associated with it. " +
        "Query this property using the provider of the task that declares it, for example 'tasks.named(...).flatMap { ... }', so that the task is known."

    def setup() {
        buildFile << """
            abstract class Report {
                @OutputFile
                abstract RegularFileProperty getDestination()
            }

            abstract class Consumer extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getSource()

                @TaskAction
                void consume() {
                    println("consumed: " + source.get().asFile.text)
                }
            }

            abstract class TextConsumer extends DefaultTask {
                @Input
                abstract Property<String> getText()

                @TaskAction
                void consume() {
                    println("consumed: " + text.get())
                }
            }

            def newReport = { String fileName ->
                def report = objects.newInstance(Report)
                report.destination = layout.buildDirectory.file(fileName)
                report
            }
        """
    }

    def "output of nested object held by #container carries dependency on the task when queried using the task provider"() {
        buildFile << """
            abstract class Producer extends DefaultTask {
                ${declaration}

                @TaskAction
                void produce() {
                    ${reports}.each { it.destination.get().asFile.text = "produced by " + name }
                }
            }

            def producer = tasks.register("producer", Producer) {
                ${configuration}
            }

            tasks.register("consumer", Consumer) {
                source = producer.flatMap { ${selector} }
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced by producer")

        where:
        container                                 | declaration                                               | reports                  | configuration                                 | selector
        "Property"                                | "@Nested abstract Property<Report> getReport()"           | "[report.get()]"         | "report = newReport('report.txt')"            | "it.report.flatMap { it.destination }"
        "Property queried by the transformation"  | "@Nested abstract Property<Report> getReport()"           | "[report.get()]"         | "report = newReport('report.txt')"            | "it.report.get().destination"
        "ListProperty"                            | "@Nested abstract ListProperty<Report> getReports()"      | "reports.get()"          | "reports.add(newReport('report.txt'))"        | "it.reports.get().first().destination"
        "mapped ListProperty"                     | "@Nested abstract ListProperty<Report> getReports()"      | "reports.get()"          | "reports.add(newReport('report.txt'))"        | "it.reports.map { it.first() }.flatMap { it.destination }"
        "MapProperty"                             | "@Nested abstract MapProperty<String, Report> getReports()" | "reports.get().values()" | "reports.put('main', newReport('report.txt'))" | "it.reports.getting('main').flatMap { it.destination }"
        "List"                                    | "@Nested List<Report> reports = []"                       | "reports"                | "reports.add(newReport('report.txt'))"        | "it.reports.first().destination"
    }

    @Issue("https://github.com/gradle/gradle/issues/29511")
    def "output of element of nested domain object container carries dependency on the task when queried using the task provider"() {
        buildFile << """
            abstract class Target {
                @Internal
                abstract String getName()

                @OutputFile
                abstract RegularFileProperty getFile()
            }

            abstract class Producer extends DefaultTask {
                @Nested
                abstract NamedDomainObjectContainer<Target> getTargets()

                @TaskAction
                void produce() {
                    targets.each { target -> target.file.get().asFile.text = "produced for " + target.name }
                }
            }

            def producer = tasks.register("producer", Producer) {
                targets.register("first") {
                    file = layout.buildDirectory.file("first.txt")
                }
                targets.register("second") {
                    file = layout.buildDirectory.file("second.txt")
                }
            }

            tasks.register("consumer", Consumer) {
                source = producer.flatMap { it.targets.named("second").flatMap { it.file } }
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced for second")
    }

    def "output of object nested several levels deep carries dependency on the task when queried using the task provider"() {
        buildFile << """
            abstract class Reports {
                @Nested
                abstract Report getFixed()

                @Nested
                abstract Property<Report> getReplaceable()
            }

            abstract class Producer extends DefaultTask {
                @Nested
                abstract Property<Reports> getReports()

                @TaskAction
                void produce() {
                    [reports.get().fixed, reports.get().replaceable.get()].each { it.destination.get().asFile.text = "produced by " + name }
                }
            }

            def nested = objects.newInstance(Reports)
            nested.fixed.destination = layout.buildDirectory.file("fixed.txt")
            nested.replaceable = newReport("replaceable.txt")
            def producer = tasks.register("producer", Producer) {
                reports = nested
            }

            tasks.register("consumeFixed", Consumer) {
                source = producer.flatMap { it.reports.flatMap { it.fixed.destination } }
            }
            tasks.register("consumeReplaceable", Consumer) {
                source = producer.flatMap { it.reports.flatMap { it.replaceable.flatMap { it.destination } } }
            }
        """

        when:
        run(consumer)

        then:
        result.assertTasksScheduled(":producer", ":$consumer")
        outputContains("consumed: produced by producer")

        where:
        consumer << ["consumeFixed", "consumeReplaceable"]
    }

    def "output of nested object carries dependency on the task when the task provider is mapped before the output is queried"() {
        buildFile << """
            ${producerWithReportProperty()}

            def producer = tasks.register("producer", Producer) {
                report = newReport("report.txt")
            }

            tasks.register("consumer", Consumer) {
                source = producer.map { it.report.get() }.flatMap { it.destination }
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced by producer")
    }

    def "output of nested object carries dependency on the task when queried using #description"() {
        buildFile << """
            ${producerWithReportProperty()}

            def producer = tasks.register("producer", Producer) {
                report = newReport("report.txt")
            }

            tasks.register("consumer", Consumer) {
                source = ${provider}
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced by producer")

        where:
        description                                          | provider
        "consecutive transformations of the task provider"   | "producer.flatMap { it.report }.flatMap { it.destination }"
        "consecutive transformations with a mapping between" | "producer.flatMap { it.report }.map { it }.flatMap { it.destination }"
        "a property that holds a transformed task provider"  | "objects.property(Report).value(producer.flatMap { it.report }).flatMap { it.destination }"
    }

    def "output of command line argument provider carries dependency on the task when queried using the task provider"() {
        buildFile << """
            abstract class ReportArguments implements org.gradle.process.CommandLineArgumentProvider {
                @OutputFile
                abstract RegularFileProperty getReport()

                @Override
                Iterable<String> asArguments() {
                    return ["--report", report.get().asFile.absolutePath]
                }
            }

            abstract class Producer extends DefaultTask {
                @Nested
                abstract ListProperty<org.gradle.process.CommandLineArgumentProvider> getArgumentProviders()

                @TaskAction
                void produce() {
                    def arguments = argumentProviders.get().collectMany { it.asArguments() }
                    new File(arguments[1]).text = "produced by " + name
                }
            }

            def reportArguments = objects.newInstance(ReportArguments)
            reportArguments.report = layout.buildDirectory.file("report.txt")
            def producer = tasks.register("producer", Producer) {
                argumentProviders.add(reportArguments)
            }

            tasks.register("consumer", Consumer) {
                source = producer.flatMap { it.argumentProviders.get().find { it instanceof ReportArguments }.report }
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced by producer")
    }

    @Issue("https://github.com/gradle/gradle/issues/28996")
    def "report of test task carries dependency on the task when queried using the task provider"() {
        buildFile << """
            apply plugin: "java-library"

            tasks.register("zipTestResults", Zip) {
                destinationDirectory = layout.buildDirectory.dir("zips")
                archiveFileName = "test-results.zip"
                from(tasks.named("test").flatMap { it.reports.junitXml.outputLocation })
            }
        """

        when:
        run("zipTestResults")

        then:
        result.assertTaskOrder(":test", ":zipTestResults")
    }

    def "content of output of nested object can be transformed #location the transformation of the task provider"() {
        buildFile << """
            ${producerWithReportProperty()}

            def producer = tasks.register("producer", Producer) {
                report = newReport("report.txt")
            }

            tasks.register("consumer", TextConsumer) {
                text = ${text}
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced by producer")

        when:
        // The content is not available when the configuration is reused either
        file("build").deleteDir()
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced by producer")

        where:
        location  | text
        "outside" | "producer.flatMap { it.report.flatMap { it.destination } }.map { it.asFile.text }"
        "inside"  | "producer.flatMap { it.report.flatMap { it.destination.map { it.asFile.text } } }"
    }

    def "cannot query content of output of nested object before the task has run when the content is transformed #location the transformation of the task provider"() {
        buildFile << """
            ${producerWithReportProperty()}

            def producer = tasks.register("producer", Producer) {
                report = newReport("report.txt")
            }

            tasks.register("consumer") {
                def text = ${text}
                println("text: " + text.get())
            }
        """
        // left behind by an earlier build
        file("build/report.txt") << "stale"

        when:
        fails("consumer")

        then:
        failure.assertThatCause(containsString("before task ':producer' has completed is not supported"))
        outputDoesNotContain("text: stale")

        where:
        location  | text
        "outside" | "producer.flatMap { it.report.flatMap { it.destination } }.map { it.asFile.text }"
        "inside"  | "producer.flatMap { it.report.flatMap { it.destination.map { it.asFile.text } } }"
    }

    def "task provider follows the nested object that replaces the one that was present when the output was wired"() {
        buildFile << """
            ${producerWithReportProperty()}

            def producer = tasks.register("producer", Producer) {
                report = newReport("first.txt")
            }

            tasks.register("consumer", Consumer) {
                source = producer.flatMap { it.report.flatMap { it.destination } }
            }

            producer.configure {
                report = newReport("second.txt")
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":producer", ":consumer")
        outputContains("consumed: produced by producer")
        file("build/second.txt").assertIsFile()
        file("build/first.txt").assertDoesNotExist()
    }

    def "each task provider carries dependency on its own task when nested object is shared by several tasks"() {
        buildFile << """
            ${producerWithReportProperty()}

            def shared = newReport("shared.txt")
            def first = tasks.register("first", Producer) {
                report = shared
            }
            def second = tasks.register("second", Producer) {
                report = shared
            }

            tasks.register("consumeFirst", Consumer) {
                source = first.flatMap { it.report.flatMap { it.destination } }
            }
            tasks.register("consumeSecond", Consumer) {
                source = second.flatMap { it.report.flatMap { it.destination } }
            }
        """

        when:
        run("consumeFirst")

        then:
        result.assertTasksScheduled(":first", ":consumeFirst")
        outputContains("consumed: produced by first")

        when:
        run("consumeSecond")

        then:
        result.assertTasksScheduled(":second", ":consumeSecond")
        outputContains("consumed: produced by second")
    }

    def "input of nested object does not carry dependency on the task when queried using #description of the task provider"() {
        buildFile << """
            abstract class Options {
                @Input
                abstract Property<String> getLabel()
            }

            abstract class Producer extends DefaultTask {
                @Nested
                abstract Property<Options> getOptions()

                @OutputFile
                abstract RegularFileProperty getDestination()

                @TaskAction
                void produce() {
                    destination.get().asFile.text = options.get().label.get()
                }
            }

            def producerOptions = objects.newInstance(Options)
            producerOptions.label = "label"
            def producer = tasks.register("producer", Producer) {
                options = producerOptions
                destination = layout.buildDirectory.file("out.txt")
            }

            tasks.register("consumer", TextConsumer) {
                text = ${provider}
            }
        """

        when:
        run("consumer")

        then:
        result.assertTasksScheduled(":consumer")
        outputContains("consumed: label")

        where:
        description                   | provider
        "a transformation"            | "producer.flatMap { it.options.flatMap { it.label } }"
        "consecutive transformations" | "producer.flatMap { it.options }.flatMap { it.label }"
    }

    def "output of nested object does not carry dependency when queried using #description"() {
        buildFile << """
            ${producerWithReportProperty()}

            def detached = newReport("report.txt")
            def producer = tasks.register("producer", Producer) {
                report = detached
            }

            tasks.register("consumer", Consumer) {
                source = ${reference}
            }
        """

        when:
        fails(requestedTasks as String[])

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':consumer'.")
        failure.assertHasCause(NO_TASK)

        where:
        description                                | reference                                        | requestedTasks
        "the object"                               | "detached.destination"                           | ["consumer"]
        "the object and the task is also requested" | "detached.destination"                           | ["producer", "consumer"]
        "the task"                                 | "producer.get().report.get().destination"        | ["consumer"]
        "a provider that is not a task provider"   | "provider { detached }.flatMap { it.destination }" | ["consumer"]
    }

    private static String producerWithReportProperty() {
        return """
            abstract class Producer extends DefaultTask {
                @Nested
                abstract Property<Report> getReport()

                @TaskAction
                void produce() {
                    report.get().destination.get().asFile.text = "produced by " + name
                }
            }
        """
    }
}
