/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.profile

import org.gradle.StartParameter
import org.gradle.api.tasks.TaskState
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class ProfileReportRendererTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "renders report"() {
        def model = new BuildProfile(new StartParameter())
        def file = temp.file("report.html")

        model.profilingStarted   = time(12, 20, 0)
        model.buildStarted       = time(12, 20, 0, 700)
        model.settingsEvaluated  = time(12, 20, 3)
        model.projectsLoaded     = time(12, 20, 6)

        model.buildFinished      = time(12, 35, 30)

        model.getDependencySetProfile("compile").start = time(12, 22, 0)
        model.getDependencySetProfile("compile").finish = time(12, 23, 30)

        model.getDependencySetProfile("runtime").start = time(12, 24, 0)
        model.getDependencySetProfile("runtime").finish = time(12, 24, 30)

        model.getTransformProfile("some transform").start(time(12, 22, 0)).setFinish(time(12, 22, 12))
        model.getTransformProfile("some other transform").start(time(12, 23, 0)).setFinish(time(12, 23, 19))

        model.getProjectProfile("a").configurationOperation.start = time(12, 20, 7)
        model.getProjectProfile("a").configurationOperation.finish = time(12, 20, 10)
        model.getProjectProfile("a").getTaskProfile("a:foo").completed(Stub(TaskState)).setStart(time(12, 25, 0)).setFinish(time(12, 26, 30))
        model.getProjectProfile("a").getTaskProfile("a:bar").completed(Stub(TaskState)).setStart(time(12, 26, 30)).setFinish(time(12, 27, 0))

        model.getProjectProfile("b").configurationOperation.start = time(12, 20, 10)
        model.getProjectProfile("b").configurationOperation.finish = time(12, 20, 15)
        //let's say they run in parallel, hence same start time
        model.getProjectProfile("b").getTaskProfile("b:foo").completed(Stub(TaskState)).setStart(time(12, 27, 0)).setFinish(time(12, 29, 30))
        model.getProjectProfile("b").getTaskProfile("b:bar").completed(Stub(TaskState)).setStart(time(12, 27, 0)).setFinish(time(12, 29, 0))

        when:
        new ProfileReportRenderer().writeTo(model, file)

        then:
        file.text.contains(toPlatformLineSeparators("""<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="x-ua-compatible" content="IE=edge"/>
<title>Profile report</title>
<link href="css/base-style.css" rel="stylesheet" type="text/css"/>
<link href="css/style.css" rel="stylesheet" type="text/css"/>
<script src="js/report.js" type="text/javascript"></script>
</head>
<body>
<div id="content">
<h1>Profile report</h1>
<div id="header">
<p>Profiled build: (no tasks specified)</p>
<p>Started on: 2010/02/05 - 12:20:00</p>
</div>
<div id="tabs">
<ul class="tabLinks">
<li>
<a href="#tab0">Summary</a>
</li>
<li>
<a href="#tab1">Configuration</a>
</li>
<li>
<a href="#tab2">Dependency Resolution</a>
</li>
<li>
<a href="#tab3">Artifact Transforms</a>
</li>
<li>
<a href="#tab4">Task Execution</a>
</li>
</ul>
<div class="tab" id="tab0">
<h2>Summary</h2>
<table>
<thead>
<tr>
<th>Description</th>
<th class="numeric">Duration</th>
</tr>
</thead>
<tr>
<td>Total Build Time</td>
<td class="numeric">15m30.00s</td>
</tr>
<tr>
<td>Startup</td>
<td class="numeric">0.700s</td>
</tr>
<tr>
<td>Settings and buildSrc</td>
<td class="numeric">2.300s</td>
</tr>
<tr>
<td>Loading Projects</td>
<td class="numeric">3.000s</td>
</tr>
<tr>
<td>Configuring Projects</td>
<td class="numeric">8.000s</td>
</tr>
<tr>
<td>Artifact Transforms</td>
<td class="numeric">31.000s</td>
</tr>
<tr>
<td>Task Execution</td>
<td class="numeric">6m30.00s</td>
</tr>
</table>
</div>
<div class="tab" id="tab1">
<h2>Configuration</h2>
<table>
<thead>
<tr>
<th>Project</th>
<th class="numeric">Duration</th>
</tr>
</thead>
<tr>
<td>All projects</td>
<td class="numeric">8.000s</td>
</tr>
<tr>
<td>b</td>
<td class="numeric">5.000s</td>
</tr>
<tr>
<td>a</td>
<td class="numeric">3.000s</td>
</tr>
</table>
</div>
<div class="tab" id="tab2">
<h2>Dependency Resolution</h2>
<table>
<thead>
<tr>
<th>Dependencies</th>
<th class="numeric">Duration</th>
</tr>
</thead>
<tr>
<td>All dependencies</td>
<td class="numeric">2m0.00s</td>
</tr>
<tr>
<td>compile</td>
<td class="numeric">1m30.00s</td>
</tr>
<tr>
<td>runtime</td>
<td class="numeric">30.000s</td>
</tr>
</table>
</div>
<div class="tab" id="tab3">
<h2>Artifact Transforms</h2>
<table>
<thead>
<tr>
<th>Transform</th>
<th class="numeric">Duration</th>
</tr>
</thead>
<tr>
<td>All transforms</td>
<td class="numeric">31.000s</td>
</tr>
<tr>
<td>some other transform</td>
<td class="numeric">19.000s</td>
</tr>
<tr>
<td>some transform</td>
<td class="numeric">12.000s</td>
</tr>
</table>
</div>
<div class="tab" id="tab4">
<h2>Task Execution</h2>
<table>
<thead>
<tr>
<th>Task</th>
<th class="numeric">Duration</th>
<th>Result</th>
</tr>
</thead>
<tr>
<td>b</td>
<td class="numeric">4m30.00s</td>
<td>(total)</td>
</tr>
<tr>
<td class="indentPath">b:foo</td>
<td class="numeric">2m30.00s</td>
<td>Did No Work</td>
</tr>
<tr>
<td class="indentPath">b:bar</td>
<td class="numeric">2m0.00s</td>
<td>Did No Work</td>
</tr>
<tr>
<td>a</td>
<td class="numeric">2m0.00s</td>
<td>(total)</td>
</tr>
<tr>
<td class="indentPath">a:foo</td>
<td class="numeric">1m30.00s</td>
<td>Did No Work</td>
</tr>
<tr>
<td class="indentPath">a:bar</td>
<td class="numeric">30.000s</td>
<td>Did No Work</td>
</tr>
</table>
</div>
</div>"""))
    }

    private static long time(int hour, int mins, int secs, int ms = 0) {
        def cal = new GregorianCalendar(2010, 1, 5, hour, mins, secs)
        cal.add(Calendar.MILLISECOND, ms)
        cal.getTimeInMillis()
    }
}
