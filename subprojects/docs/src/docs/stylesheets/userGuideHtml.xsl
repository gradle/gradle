<!--
  ~ Copyright 2010 the original author or authors.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->
<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:import href="html/chunkfast.xsl"/>
    <xsl:import href="userGuideHtmlCommon.xsl"/>

    <xsl:param name="root.filename">userguide</xsl:param>
    <xsl:param name="chunk.section.depth">0</xsl:param>
    <xsl:param name="chunk.quietly">1</xsl:param>
    <xsl:param name="use.id.as.filename">1</xsl:param>

    <xsl:param name="generate.toc">
        book toc,title,example
        part toc,title
        chapter toc,title
    </xsl:param>

    <!--
      Customize HTML page titles to include "User Guide" and version to help
      with Google results. See issue doc-portal#9.
    -->
    <xsl:template match="chapter" mode="object.title.markup.textonly">
        <xsl:value-of select="title"/>
        <xsl:text> - </xsl:text>
        <xsl:apply-templates select="/book" mode="object.title.markup.textonly"/>
    </xsl:template>

    <xsl:template name="chunk-element-content">
        <xsl:param name="content">
            <xsl:apply-imports/>
        </xsl:param>

        <html lang="en">
            <xsl:call-template name="html.head"></xsl:call-template>
            <body>
                <xsl:call-template name="header.navigation"></xsl:call-template>
                <main class="main-content">
                    <nav class="docs-navigation">
                        <ul>
                            <li><a href="/userguide/userguide.html">Overview</a></li>
                            <li><a href="/dsl/">DSL Reference</a></li>
                            <li><a href="/release-notes.html">Release Notes</a></li>
                        </ul>

                        <h3 id="getting-started">Getting Started</h3>
                        <ul>
                            <li><a href="/userguide/installation.html">Installing Gradle</a></li>
                            <li><a href="https://guides.gradle.org/creating-new-gradle-builds/">Creating a New Gradle Build</a></li>
                            <li><a href="https://guides.gradle.org/creating-build-scans/">Creating Build Scans</a></li>
                            <li><a href="https://guides.gradle.org/migrating-from-maven/">Migrating from Maven</a></li>
                        </ul>

                        <h3 id="using-gradle-builds">Using Gradle Builds</h3>
                        <ul>
                            <li><a href="/userguide/build_cache.html">Build Cache</a></li>
                            <li><a href="/userguide/build_environment.html">Build Environment</a></li>
                            <li><a href="https://docs.gradle.com/build-scan-plugin">Build Scans</a></li>
                            <li><a href="/userguide/command_line_interface.html">Command-Line Interface</a></li>
                            <li><a href="/userguide/composite_builds.html">Composite Builds</a></li>
                            <li><a href="/userguide/continuous_build.html">Continuous Build</a></li>
                            <li><a href="/userguide/gradle_daemon.html">Daemon</a></li>
                            <li><a href="/userguide/embedding.html">Embedding Gradle</a></li>
                            <li><a href="/userguide/gradle_wrapper.html">Gradle Wrapper</a></li>
                            <li><a href="/userguide/init_scripts.html">Init Scripts</a></li>
                            <li><a href="/userguide/intro_multi_project_builds.html">Multi-Project Builds</a></li>
                            <li><a href="/userguide/troubleshooting.html">Troubleshooting</a></li>
                        </ul>

                        <h3 id="writing-gradle-builds">Writing Gradle Builds</h3>
                        <ul>
                            <li><a href="/dsl/">DSL Reference</a></li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#building-jvm-projects" aria-expanded="false" aria-controls="building-jvm-projects">JVM Projects</a>
                                <ul id="building-jvm-projects">
                                    <li><a class="nav-dropdown" data-toggle="collapse" href="#java-tutorials" aria-expanded="false" aria-controls="java-tutorials">JVM Tutorials</a>
                                        <ul id="java-tutorials">
                                            <li><a href="https://guides.gradle.org/building-groovy-libraries/">Building Groovy Libraries</a></li>
                                            <li><a href="https://guides.gradle.org/building-java-libraries/">Building Java Libraries</a></li>
                                            <li><a href="https://guides.gradle.org/building-java-9-modules/">Building Java 9 Modules</a></li>
                                            <li><a href="https://guides.gradle.org/building-java-applications/">Building Java Applications</a></li>
                                            <li><a href="https://guides.gradle.org/building-java-web-applications/">Building Java Web Applications</a></li>
                                            <li><a href="https://guides.gradle.org/building-kotlin-jvm-libraries/">Building Kotlin JVM Libraries</a></li>
                                            <li><a href="https://guides.gradle.org/building-scala-libraries/">Building Scala Libraries</a></li>
                                            <li><a href="https://guides.gradle.org/consuming-jvm-libraries/">Consuming JVM Libraries</a></li>
                                            <li><a href="https://guides.gradle.org/creating-multi-project-builds/">Creating Multi-project Builds</a></li>
                                            <li><a href="/userguide/tutorial_groovy_projects.html">Groovy Quickstart</a></li>
                                            <li><a href="/userguide/tutorial_java_projects.html">Java Quickstart</a></li>
                                            <li><a href="/userguide/web_project_tutorial.html">Web Application Quickstart</a></li>
                                            <li><a href="https://guides.gradle.org/writing-gradle-tasks/">Writing Custom Script Tasks</a></li>
                                            <li><a href="/userguide/custom_tasks.html">Writing Custom Task Classes</a></li>
                                        </ul>
                                    </li>
                                    <li><a class="nav-dropdown" data-toggle="collapse" href="#java-plugins-reference" aria-expanded="false" aria-controls="java-plugins-reference">Plugins Reference</a>
                                        <ul id="java-plugins-reference">
                                            <li><a href="/userguide/antlr_plugin.html">ANTLR Plugin</a></li>
                                            <li><a href="/userguide/application_plugin.html">Application Plugin</a></li>
                                            <li><a href="/userguide/checkstyle_plugin.html">Checkstyle Plugin</a></li>
                                            <li><a href="/userguide/codenarc_plugin.html">CodeNarc Plugin</a></li>
                                            <li><a href="/userguide/ear_plugin.html">EAR Plugin</a></li>
                                            <li><a href="/userguide/eclipse_plugin.html">Eclipse Plugin</a></li>
                                            <li><a href="/userguide/findbugs_plugin.html">FindBugs Plugin</a></li>
                                            <li><a href="/userguide/groovy_plugin.html">Groovy Plugin</a></li>
                                            <li><a href="/userguide/idea_plugin.html">IDEA Plugin</a></li>
                                            <li><a href="/userguide/jacoco_plugin.html">JaCoCo Plugin</a></li>
                                            <li><a href="/userguide/java_plugin.html">Java Plugin</a></li>
                                            <li><a href="/userguide/java_library_plugin.html">Java Library Plugin</a></li>
                                            <li><a href="/userguide/java_library_distribution_plugin.html">Java Library Distribution Plugin</a></li>
                                            <li><a href="/userguide/java_software.html">Java Software Model</a></li>
                                            <li><a href="/userguide/jetty_plugin.html">Jetty Plugin</a></li>
                                            <li><a href="/userguide/jdepend_plugin.html">JDepend Plugin</a></li>
                                            <li><a href="/userguide/osgi_plugin.html">OSGi Plugin</a></li>
                                            <li><a href="/userguide/play_plugin.html">Play Plugin</a></li>
                                            <li><a href="/userguide/project_reports_plugin.html">Project Report Plugin</a></li>
                                            <li><a href="/userguide/pmd_plugin.html">PMD Plugin</a></li>
                                            <li><a href="/userguide/scala_plugin.html">Scala Plugin</a></li>
                                            <li><a href="/userguide/war_plugin.html">WAR Plugin</a></li>
                                        </ul>
                                    </li>
                                    <li><a href="/userguide/ant.html">Using Ant from Gradle</a></li>
                                </ul>
                            </li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#building-cpp-projects" aria-expanded="false" aria-controls="building-native-projects">C++ Projects</a>
                                <ul id="building-cpp-projects">
                                    <li><a class="nav-dropdown" data-toggle="collapse" href="#native-tutorials" aria-expanded="false" aria-controls="cpp-tutorials">C++ Tutorials</a>
                                        <ul id="cpp-tutorials">
                                            <li><a href="https://guides.gradle.org/building-cpp-executables/">Building C++ Executables</a></li>
                                            <li><a href="https://guides.gradle.org/building-cpp-executables/">Building C++ Libraries</a></li>
                                        </ul>
                                    </li>
                                    <li><a href="/userguide/native_software.html">Building Native Software</a></li>
                                    <li><a href="/userguide/software_model_concepts.html">Software Model Concepts</a></li>
                                    <li><a href="/userguide/software_model.html">Rule-based Model Configuration</a></li>
                                    <li><a href="/userguide/rule_source.html">Implementing Model Rules in a Plugin</a></li>
                                    <li><a href="/userguide/software_model_extend.html">Extending the Software Model</a></li>
                                </ul>
                            </li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#building-android-projects" aria-expanded="false" aria-controls="building-android-projects">Android Projects</a>
                                <ul id="building-android-projects">
                                    <li><a href="https://guides.gradle.org/building-android-apps/">Building Android Apps</a></li>
                                </ul>
                            </li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#building-javascript-projects" aria-expanded="false" aria-controls="building-javascript-projects">JavaScript Projects</a>
                                <ul id="building-javascript-projects">
                                    <li><a href="https://guides.gradle.org/running-webpack-with-gradle/">Bundling JavaScript with Webpack</a></li>
                                </ul>
                            </li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#authoring-build-scripts" aria-expanded="false" aria-controls="authoring-build-scripts">Authoring Build Scripts</a>
                                <ul id="authoring-build-scripts">
                                    <li><a href="/userguide/tutorial_using_tasks.html">Build Script Basics</a></li>
                                    <li><a href="/userguide/more_about_tasks.html">Authoring Tasks</a></li>
                                    <li><a href="/userguide/logging.html">Logging</a></li>
                                    <li><a href="/userguide/multi_project_builds.html">Multi-project Builds</a></li>
                                    <li><a href="/userguide/standard_plugins.html">Standard Gradle Plugins</a></li>
                                    <li><a href="/userguide/plugins.html">Using Gradle Plugins</a></li>
                                    <li><a href="/userguide/writing_build_scripts.html">Writing Build Scripts</a></li>
                                    <li><a href="/userguide/working_with_files.html">Working with Files</a></li>
                                    <li><a href="/userguide/build_lifecycle.html">Build Lifecycle</a></li>
                                    <li><a href="/userguide/feature_lifecycle.html">Feature Lifecycle</a></li></ul></li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#managing-dependencies" aria-expanded="false" aria-controls="managing-dependencies">Managing Dependencies</a>
                                <ul id="managing-dependencies">
                                    <li><a href="/userguide/artifact_dependencies_tutorial.html">Dependency Management Basics</a></li>
                                    <li><a href="/userguide/dependency_management.html">Advanced Dependency Management</a></li></ul></li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#publishing-artifacts" aria-expanded="false" aria-controls="publishing-artifacts">Publishing Artifacts</a>
                                <ul id="publishing-artifacts">
                                    <li><a href="/userguide/artifact_management.html">Publishing Artifacts Overview</a></li>
                                    <li><a href="/userguide/distribution_plugin.html">Distribution Plugin</a></li>
                                    <li><a href="/userguide/maven_plugin.html">Maven Plugin</a></li>
                                    <li><a href="/userguide/publishing_maven.html">Maven Publish Plugin</a></li>
                                    <li><a href="/userguide/publishing_ivy.html">Ivy Publish Plugin</a></li>
                                    <li><a href="/userguide/signing_plugin.html">Signing Plugin</a></li></ul></li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#best-practices" aria-expanded="false" aria-controls="best-practices">Best Practices</a>
                                <ul id="best-practices">
                                    <li><a href="/userguide/organizing_build_logic.html">Organizing Build Logic</a></li>
                                    <li><a href="https://guides.gradle.org/performance/">Optimizing Build Performance</a></li></ul></li>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#sample-gradle-builds" aria-expanded="false" aria-controls="sample-gradle-builds">Sample Gradle Builds</a>
                                <ul id="sample-gradle-builds">
                                    <li><a href="https://github.com/gradle/gradle/tree/master/subprojects/docs/src/samples">Sample Projects</a></li>
                                    <li><a href="https://github.com/gradle/native-samples">Native Samples</a></li>
                                    <li><a href="https://github.com/gradle/kotlin-dsl/tree/master/samples">Kotlin DSL Samples</a></li>
                                </ul>
                            </li>
                        </ul>

                        <h3 id="developing-gradle-plugins">Developing Gradle Plugins</h3>
                        <ul>
                            <li><a class="nav-dropdown" data-toggle="collapse" href="#plugins-tutorials" aria-expanded="false" aria-controls="plugins-tutorials">Gradle Plugins Tutorials</a>
                                <ul id="plugins-tutorials">
                                    <li><a href="https://guides.gradle.org/designing-gradle-plugins/">Designing Gradle Plugins</a></li>
                                    <li><a href="https://guides.gradle.org/implementing-gradle-plugins/">Implementing Gradle Plugins</a></li>
                                    <li><a href="https://guides.gradle.org/testing-gradle-plugins/">Testing Gradle Plugins</a></li>
                                    <li><a href="https://guides.gradle.org/publishing-plugins-to-gradle-plugin-portal/">Publishing Gradle Plugins</a></li>
                                </ul>
                            </li>
                            <li><a href="https://guides.gradle.org/using-build-cache/">Developing Cacheable Tasks</a></li>
                            <li><a href="https://guides.gradle.org/using-the-worker-api/">Developing Parallel Tasks</a></li>
                            <li><a href="/userguide/lazy_configuration.html">Lazy Task Configuration</a></li>
                            <li><a href="/userguide/java_gradle_plugin.html">Plugin Development Plugin</a></li>
                            <li><a href="/userguide/test_kit.html">Testing with TestKit</a></li>
                            <li><a href="/userguide/custom_plugins.html">Writing Custom Plugins</a></li>
                        </ul>
                    </nav>
                    <xsl:copy-of select="$content"/>
                    <aside class="secondary-navigation"></aside>
                </main>
                <xsl:call-template name="footer.navigation"></xsl:call-template>
                <script type="text/javascript">
                    // Polyfill Element.matches()
                    if (!Element.prototype.matches) {
                        Element.prototype.matches = Element.prototype.msMatchesSelector || Element.prototype.webkitMatchesSelector;
                    }
                    // Polyfill Element.closest()
                    if (!Element.prototype.closest) {
                        Element.prototype.closest = function(s) {
                            var el = this;
                            if (!document.documentElement.contains(el)) return null;
                            do {
                                if (typeof el.matches === "function" &amp;&amp; el.matches(s)) return el;
                                el = el.parentElement || el.parentNode;
                            } while (el !== null);
                            return null;
                        };
                    }
                    [].forEach.call(document.querySelectorAll(".docs-navigation a[href$='"+ window.location.pathname +"']"), function(link) {
                        // Add "active" to all links same as current URL
                        link.classList.add("active");

                        // Expand all parent navigation
                        var parentListEl = link.closest("li");
                        while (parentListEl !== null) {
                            var dropDownEl = parentListEl.querySelector(".nav-dropdown");
                            if (dropDownEl !== null) {
                                dropDownEl.classList.add("expanded");
                            }
                            parentListEl = parentListEl.parentNode.closest("li");
                        }
                    });

                    // Expand/contract multi-level side navigation
                    [].forEach.call(document.querySelectorAll(".docs-navigation .nav-dropdown"), function registerSideNavActions(collapsibleElement) {
                        collapsibleElement.addEventListener("click", function toggleExpandedSideNav(evt) {
                            evt.preventDefault();
                            evt.target.classList.toggle("expanded");
                            evt.target.setAttribute("aria-expanded", evt.target.classList.contains("expanded").toString());
                            return false;
                        }, false);
                    });
                </script>
            </body>
        </html>
        <xsl:value-of select="$chunk.append"/>
    </xsl:template>

    <xsl:template name="chapter.titlepage.before.recto">
        <aside class="chapter-meta js-chapter-meta">
            <div class="rating js-rating-widget">
                <!--NOTE: These are "backwards" because we use a right-to-left trick for hover state-->
                <i class="star js-analytics-event js-rating" title="Excellent Documentation" data-action="rating" data-label="5"><svg width="16px" height="15px" viewBox="0 0 16 15" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="#999999" transform="translate(-33.000000, -11.000000)" stroke-width="1" fill="none" fill-rule="evenodd"><path d="M40.9955595,22.8514234 L36.7915654,24.9948806 L36.7915654,24.9948806 C36.7423632,25.019967 36.6821404,25.0004172 36.657054,24.951215 C36.6471785,24.931846 36.6438844,24.9097862 36.6476706,24.8883772 L37.4490484,20.357021 L37.4490484,20.357021 C37.4549065,20.3238963 37.4437179,20.2900458 37.419273,20.2669371 L34.0268385,17.0599455 L34.0268385,17.0599455 C33.9867045,17.0220055 33.984926,16.9587139 34.0228661,16.91858 C34.0384357,16.9021101 34.0591382,16.8914177 34.0815807,16.888255 L38.7752895,16.2268062 L38.7752895,16.2268062 C38.8076013,16.2222528 38.8356591,16.2022207 38.8504587,16.1731387 L40.9518588,12.0437603 L40.9518588,12.0437603 C40.9769072,11.9945387 41.0371149,11.9749424 41.0863365,11.9999908 C41.1051882,12.0095843 41.1205124,12.0249085 41.1301059,12.0437603 L43.2315061,16.1731387 L43.2315061,16.1731387 C43.2463056,16.2022207 43.2743635,16.2222528 43.3066753,16.2268062 L48.0003841,16.888255 L48.0003841,16.888255 C48.0550722,16.8959618 48.0931581,16.9465428 48.0854513,17.001231 C48.0822887,17.0236735 48.0715962,17.044376 48.0551263,17.0599455 L44.6626917,20.2669371 L44.6626917,20.2669371 C44.6382468,20.2900458 44.6270582,20.3238963 44.6329164,20.357021 L45.4342941,24.8883772 L45.4342941,24.8883772 C45.4439121,24.9427617 45.4076217,24.9946461 45.3532371,25.0042641 C45.3318281,25.0080503 45.3097683,25.0047561 45.2903993,24.9948806 L41.0864052,22.8514234 L41.0864052,22.8514234 C41.0578708,22.8368748 41.024094,22.8368748 40.9955595,22.8514234 Z"></path></g></svg></i>
                <i class="star js-analytics-event js-rating" title="Good Documentation" data-action="rating" data-label="4"><svg width="16px" height="15px" viewBox="0 0 16 15" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="#999999" transform="translate(-33.000000, -11.000000)" stroke-width="1" fill="none" fill-rule="evenodd"><path d="M40.9955595,22.8514234 L36.7915654,24.9948806 L36.7915654,24.9948806 C36.7423632,25.019967 36.6821404,25.0004172 36.657054,24.951215 C36.6471785,24.931846 36.6438844,24.9097862 36.6476706,24.8883772 L37.4490484,20.357021 L37.4490484,20.357021 C37.4549065,20.3238963 37.4437179,20.2900458 37.419273,20.2669371 L34.0268385,17.0599455 L34.0268385,17.0599455 C33.9867045,17.0220055 33.984926,16.9587139 34.0228661,16.91858 C34.0384357,16.9021101 34.0591382,16.8914177 34.0815807,16.888255 L38.7752895,16.2268062 L38.7752895,16.2268062 C38.8076013,16.2222528 38.8356591,16.2022207 38.8504587,16.1731387 L40.9518588,12.0437603 L40.9518588,12.0437603 C40.9769072,11.9945387 41.0371149,11.9749424 41.0863365,11.9999908 C41.1051882,12.0095843 41.1205124,12.0249085 41.1301059,12.0437603 L43.2315061,16.1731387 L43.2315061,16.1731387 C43.2463056,16.2022207 43.2743635,16.2222528 43.3066753,16.2268062 L48.0003841,16.888255 L48.0003841,16.888255 C48.0550722,16.8959618 48.0931581,16.9465428 48.0854513,17.001231 C48.0822887,17.0236735 48.0715962,17.044376 48.0551263,17.0599455 L44.6626917,20.2669371 L44.6626917,20.2669371 C44.6382468,20.2900458 44.6270582,20.3238963 44.6329164,20.357021 L45.4342941,24.8883772 L45.4342941,24.8883772 C45.4439121,24.9427617 45.4076217,24.9946461 45.3532371,25.0042641 C45.3318281,25.0080503 45.3097683,25.0047561 45.2903993,24.9948806 L41.0864052,22.8514234 L41.0864052,22.8514234 C41.0578708,22.8368748 41.024094,22.8368748 40.9955595,22.8514234 Z"></path></g></svg></i>
                <i class="star js-analytics-event js-rating" title="OK Documentation" data-action="rating" data-label="3"><svg width="16px" height="15px" viewBox="0 0 16 15" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="#999999" transform="translate(-33.000000, -11.000000)" stroke-width="1" fill="none" fill-rule="evenodd"><path d="M40.9955595,22.8514234 L36.7915654,24.9948806 L36.7915654,24.9948806 C36.7423632,25.019967 36.6821404,25.0004172 36.657054,24.951215 C36.6471785,24.931846 36.6438844,24.9097862 36.6476706,24.8883772 L37.4490484,20.357021 L37.4490484,20.357021 C37.4549065,20.3238963 37.4437179,20.2900458 37.419273,20.2669371 L34.0268385,17.0599455 L34.0268385,17.0599455 C33.9867045,17.0220055 33.984926,16.9587139 34.0228661,16.91858 C34.0384357,16.9021101 34.0591382,16.8914177 34.0815807,16.888255 L38.7752895,16.2268062 L38.7752895,16.2268062 C38.8076013,16.2222528 38.8356591,16.2022207 38.8504587,16.1731387 L40.9518588,12.0437603 L40.9518588,12.0437603 C40.9769072,11.9945387 41.0371149,11.9749424 41.0863365,11.9999908 C41.1051882,12.0095843 41.1205124,12.0249085 41.1301059,12.0437603 L43.2315061,16.1731387 L43.2315061,16.1731387 C43.2463056,16.2022207 43.2743635,16.2222528 43.3066753,16.2268062 L48.0003841,16.888255 L48.0003841,16.888255 C48.0550722,16.8959618 48.0931581,16.9465428 48.0854513,17.001231 C48.0822887,17.0236735 48.0715962,17.044376 48.0551263,17.0599455 L44.6626917,20.2669371 L44.6626917,20.2669371 C44.6382468,20.2900458 44.6270582,20.3238963 44.6329164,20.357021 L45.4342941,24.8883772 L45.4342941,24.8883772 C45.4439121,24.9427617 45.4076217,24.9946461 45.3532371,25.0042641 C45.3318281,25.0080503 45.3097683,25.0047561 45.2903993,24.9948806 L41.0864052,22.8514234 L41.0864052,22.8514234 C41.0578708,22.8368748 41.024094,22.8368748 40.9955595,22.8514234 Z"></path></g></svg></i>
                <i class="star js-analytics-event js-rating" title="Poor Documentation" data-action="rating" data-label="2"><svg width="16px" height="15px" viewBox="0 0 16 15" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="#999999" transform="translate(-33.000000, -11.000000)" stroke-width="1" fill="none" fill-rule="evenodd"><path d="M40.9955595,22.8514234 L36.7915654,24.9948806 L36.7915654,24.9948806 C36.7423632,25.019967 36.6821404,25.0004172 36.657054,24.951215 C36.6471785,24.931846 36.6438844,24.9097862 36.6476706,24.8883772 L37.4490484,20.357021 L37.4490484,20.357021 C37.4549065,20.3238963 37.4437179,20.2900458 37.419273,20.2669371 L34.0268385,17.0599455 L34.0268385,17.0599455 C33.9867045,17.0220055 33.984926,16.9587139 34.0228661,16.91858 C34.0384357,16.9021101 34.0591382,16.8914177 34.0815807,16.888255 L38.7752895,16.2268062 L38.7752895,16.2268062 C38.8076013,16.2222528 38.8356591,16.2022207 38.8504587,16.1731387 L40.9518588,12.0437603 L40.9518588,12.0437603 C40.9769072,11.9945387 41.0371149,11.9749424 41.0863365,11.9999908 C41.1051882,12.0095843 41.1205124,12.0249085 41.1301059,12.0437603 L43.2315061,16.1731387 L43.2315061,16.1731387 C43.2463056,16.2022207 43.2743635,16.2222528 43.3066753,16.2268062 L48.0003841,16.888255 L48.0003841,16.888255 C48.0550722,16.8959618 48.0931581,16.9465428 48.0854513,17.001231 C48.0822887,17.0236735 48.0715962,17.044376 48.0551263,17.0599455 L44.6626917,20.2669371 L44.6626917,20.2669371 C44.6382468,20.2900458 44.6270582,20.3238963 44.6329164,20.357021 L45.4342941,24.8883772 L45.4342941,24.8883772 C45.4439121,24.9427617 45.4076217,24.9946461 45.3532371,25.0042641 C45.3318281,25.0080503 45.3097683,25.0047561 45.2903993,24.9948806 L41.0864052,22.8514234 L41.0864052,22.8514234 C41.0578708,22.8368748 41.024094,22.8368748 40.9955595,22.8514234 Z"></path></g></svg></i>
                <i class="star js-analytics-event js-rating" title="Unusable Documentation" data-action="rating" data-label="1"><svg width="16px" height="15px" viewBox="0 0 16 15" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="#999999" transform="translate(-33.000000, -11.000000)" stroke-width="1" fill="none" fill-rule="evenodd"><path d="M40.9955595,22.8514234 L36.7915654,24.9948806 L36.7915654,24.9948806 C36.7423632,25.019967 36.6821404,25.0004172 36.657054,24.951215 C36.6471785,24.931846 36.6438844,24.9097862 36.6476706,24.8883772 L37.4490484,20.357021 L37.4490484,20.357021 C37.4549065,20.3238963 37.4437179,20.2900458 37.419273,20.2669371 L34.0268385,17.0599455 L34.0268385,17.0599455 C33.9867045,17.0220055 33.984926,16.9587139 34.0228661,16.91858 C34.0384357,16.9021101 34.0591382,16.8914177 34.0815807,16.888255 L38.7752895,16.2268062 L38.7752895,16.2268062 C38.8076013,16.2222528 38.8356591,16.2022207 38.8504587,16.1731387 L40.9518588,12.0437603 L40.9518588,12.0437603 C40.9769072,11.9945387 41.0371149,11.9749424 41.0863365,11.9999908 C41.1051882,12.0095843 41.1205124,12.0249085 41.1301059,12.0437603 L43.2315061,16.1731387 L43.2315061,16.1731387 C43.2463056,16.2022207 43.2743635,16.2222528 43.3066753,16.2268062 L48.0003841,16.888255 L48.0003841,16.888255 C48.0550722,16.8959618 48.0931581,16.9465428 48.0854513,17.001231 C48.0822887,17.0236735 48.0715962,17.044376 48.0551263,17.0599455 L44.6626917,20.2669371 L44.6626917,20.2669371 C44.6382468,20.2900458 44.6270582,20.3238963 44.6329164,20.357021 L45.4342941,24.8883772 L45.4342941,24.8883772 C45.4439121,24.9427617 45.4076217,24.9946461 45.3532371,25.0042641 C45.3318281,25.0080503 45.3097683,25.0047561 45.2903993,24.9948806 L41.0864052,22.8514234 L41.0864052,22.8514234 C41.0578708,22.8368748 41.024094,22.8368748 40.9955595,22.8514234 Z"></path></g></svg></i>
            </div>

            <div class="quick-edit">
                <a class="edit-link js-edit-link" href="https://github.com/gradle/gradle/edit/master/subprojects/docs/src/docs/userguide/">
                    <svg width="11px" height="12px" viewBox="0 0 11 12" version="1.1" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                        <g stroke="#999999" stroke-width="1" fill="none" fill-rule="evenodd" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="9 5.11724219 9 11.5 0.5 11.5 0.5 2.5 5 2.5"></polyline>
                            <polygon fill="#999999" points="9.59427002 0.565307617 4.31427002 5.84530762 4.31427002 6.56530762 5.03427002 6.56530762 10.31427 1.28530762"></polygon>
                        </g>
                    </svg>
                    Edit this page
                </a>
            </div>
        </aside>
    </xsl:template>
</xsl:stylesheet>
