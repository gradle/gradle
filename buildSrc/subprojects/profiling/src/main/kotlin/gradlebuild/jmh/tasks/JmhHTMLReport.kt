/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.jmh.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction


abstract class JmhHTMLReport : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val csv: RegularFileProperty

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun render() {
        var first = true
        val benchmarks = mutableMapOf<String, MutableList<Pair<String, Int>>>().withDefault { mutableListOf() }
        csv.get().asFile.forEachLine { line ->
            if (first) {
                first = false
            } else {
                val tokens = line.replace("\"", "").split(",")

                val (benchmark, _, _, _, score) = tokens.subList(0, 5)
                val (_, _, accessor) = tokens.subList(5, tokens.size)
                val name = benchToScenarioName(benchmark)
                val benchmarksValue = benchmarks.getValue(name)
                benchmarksValue.add(Pair(accessor.replace("FileMetadataAccessor", ""), score.toDouble().toInt()))
                benchmarks[name] = benchmarksValue
            }
        }

        val outputCsv = destination.file("data.csv").get().asFile
        outputCsv.parentFile.mkdirs()
        val tested = mutableSetOf<String>()
        benchmarks.forEach { (_, values) ->
            values.forEach {
                tested.add(it.first)
            }
        }
        outputCsv.printWriter().use { writer ->
            writer.print("Scenario,${tested.joinToString(",")}\n")
            benchmarks.forEach { (benchmark, values) ->
                writer.print(benchmark)
                tested.forEach { test ->
                    writer.print(",${values.find { it.first == test }!!.second}")
                }
                writer.print("\n")
            }
        }

        val outputHtml = destination.file("index.html").get().asFile
        outputHtml.writeText(htmlFileContent)
    }

    private
    fun benchToScenarioName(benchmark: String) = benchmark.substring(benchmark.lastIndexOf(".") + 1)

    private
    val htmlFileContent = """
        <!DOCTYPE html>
        <style>
        #chart {
            width: 50%
        }
        </style>
        <div id="chart"></div>
        <!-- Load c3.css -->
        <link href="https://cdnjs.cloudflare.com/ajax/libs/c3/0.4.11/c3.min.css" rel="stylesheet" type="text/css">

        <!-- Load d3.js and c3.js -->
        <script src="https://cdnjs.cloudflare.com/ajax/libs/d3/3.5.4/d3.min.js" charset="utf-8"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/c3/0.4.11/c3.min.js"></script>
        <script>
        var chart = c3.generate({
            data: {
                url: 'data.csv',
                type: 'bar',
                x: 'Scenario'
            },
            axis: {
                x: {
                    type: 'category'
                },
                y: {
                    label: 'ops/s',
                    tick: {
                        format: function (x) { return (x / 1000000) + "M" }
                    }
                }
            }
        });
        </script>
    """.trimIndent()
}
