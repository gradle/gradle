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
                            <li><a href="/userguide/command_line_interface.html">Command-Line Interface</a></li>
                            <li><a href="/userguide/gradle_wrapper.html">Gradle Wrapper</a></li>
                            <li><a href="https://docs.gradle.com/build-scan-plugin">Build Scans</a></li>
                            <li><a href="/userguide/build_environment.html">Build Environment</a></li>
                            <li><a href="/userguide/init_scripts.html">Init Scripts</a></li>
                            <li><a href="/userguide/intro_multi_project_builds.html">Multi-Project Builds</a></li>
                            <li><a href="/userguide/build_cache.html">Build Cache</a></li>
                            <li><a href="/userguide/composite_builds.html">Composite Builds</a></li>
                            <li><a href="/userguide/continuous_build.html">Continuous Build</a></li>
                            <li><a href="/userguide/gradle_daemon.html">Daemon</a></li>
                            <li><a href="/userguide/troubleshooting.html">Troubleshooting</a></li>
                            <li><a href="/userguide/embedding.html">Embedding Gradle</a></li>
                        </ul>

                        <h3 id="writing-gradle-builds">Authoring Gradle Builds</h3>
                        <ul>
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
                                    <li><a href="/userguide/test_kit.html">Testing with TestKit</a></li>
                                    <li><a href="/userguide/multi_project_builds.html">Configuring Multi-Project Builds</a></li>
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

    <!-- BOOK TITLEPAGE -->
    <xsl:template name="book.titlepage">
        <main class="home">
            <header>
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/title"/>
            </header>

            <p class="lead">Gradle is an open-source build automation tool focused on flexibility and performance. Gradle build scripts are written using a <a href="http://groovy-lang.org/">Groovy</a> or <a href="https://kotlinlang.org/">Kotlin</a> DSL.</p>

            <ul>
                <li><strong>Highly customizable</strong> — Gradle is modeled in a way that customizable and extensible in the most fundamental ways.</li>
                <li><strong>Fast</strong> — Gradle completes tasks quickly by reusing outputs from previous executions, processing only inputs that changed, and executing tasks in parallel.</li>
                <li><strong>Open source</strong> — Gradle is an open source project, licensed under the Apache License v2.0.</li>
                <li><strong>Powerful</strong> — Gradle is the official build tool for Android, and comes with support for many popular languages and technologies.</li>
            </ul>

            <div class="technology-logos">
                <div class="technology-logo logo-java"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd"><g fill-rule="nonzero"><g transform="translate(12.000000, 2.000000)"><path d="M19.183327,61.4235105 C19.183327,61.4235105 16.0927127,63.3086723 21.3828119,63.9465864 C27.7916185,64.7134551 31.0670854,64.6034778 38.1296339,63.2014387 C38.1296339,63.2014387 39.9864439,64.4226212 42.5796127,65.4803239 C26.7472446,72.5975243 6.74784437,65.0680805 19.183327,61.4235105" fill="#4CA5AF"></path><path d="M17.1523256,52.7665056 C17.1523256,52.7665056 13.6984279,55.3506468 18.9733216,55.9021176 C25.7946719,56.6134095 31.1816403,56.6715862 40.5031676,54.8573527 C40.5031676,54.8573527 41.7924606,56.1785116 43.8197956,56.9009998 C24.746687,62.5382079 3.5026748,57.3455572 17.1523256,52.7665056" fill="#4CA5AF"></path><path d="M33.2898906,38.5120507 C37.1335976,42.9771464 32.2799939,46.9951691 32.2799939,46.9951691 C32.2799939,46.9951691 42.0398225,41.9115827 37.5575738,35.5457223 C33.3712923,29.6091648 30.1609722,26.6595231 47.5403443,16.489533 C47.5403443,16.489533 20.2604625,23.364012 33.2898906,38.5120507" fill="#1C8789"></path><path d="M54.2563689,67.8505555 C54.2563689,67.8505555 56.532228,69.6570016 51.7499304,71.0545159 C42.656255,73.7082886 13.901083,74.5096443 5.91301706,71.1602489 C3.04151277,69.956857 8.42639675,68.2868608 10.1202763,67.9364374 C11.8868219,67.5674168 12.8963385,67.6361641 12.8963385,67.6361641 C9.7029342,65.4690557 -7.74452373,71.8913946 4.0338932,73.7306471 C36.1553498,78.748785 62.5882431,71.4709701 54.2563689,67.8505555" fill="#4CA5AF"></path><path d="M20.7745986,43.7109862 C20.7745986,43.7109862 6.11812587,47.3506463 15.5843758,48.672341 C19.581319,49.2318342 27.5491228,49.1052551 34.9709305,48.4550886 C41.0364423,47.9201385 47.12695,46.7827448 47.12695,46.7827448 C47.12695,46.7827448 44.9881756,47.7403826 43.4408246,48.8450521 C28.5574331,52.9376245 -0.19451871,51.0337114 8.08279875,46.8475114 C15.0829376,43.3096601 20.7745986,43.7109862 20.7745986,43.7109862" fill="#4CA5AF"></path><path d="M47.0176174,58.5589978 C62.1463733,50.7993448 55.1514319,43.3423894 50.2690567,44.3470189 C49.0723706,44.5928667 48.5387955,44.805892 48.5387955,44.805892 C48.5387955,44.805892 48.9830429,44.1189768 49.8315469,43.8216425 C59.4904516,40.469874 66.9187733,53.7072571 46.7135557,58.9500805 C46.7135557,58.9502949 46.9476332,58.7437055 47.0176174,58.5589978" fill="#4CA5AF"></path><path d="M37.9619675,0 C37.9619675,0 46.2921791,8.27895025 30.0611238,21.0096369 C17.0454653,31.2218379 27.0931483,37.0445614 30.0557216,43.6972625 C22.4582661,36.8869816 16.8827507,30.8918649 20.6232426,25.3121663 C26.113403,17.1216669 41.323008,13.1506132 37.9619675,0" fill="#1C8789"></path><path d="M22.0590017,79.3158167 C36.4667145,80.2185961 58.5913894,78.8149292 59.1153846,72.1417069 C59.1153846,72.1417069 58.1081493,74.6714735 47.2081857,76.6805113 C34.9109015,78.9457974 19.7439347,78.681317 10.7482517,77.2295244 C10.7484674,77.2293134 12.5897824,78.7214218 22.0590017,79.3158167" fill="#4CA5AF"></path></g></g></g></svg></div>
                <div class="technology-logo logo-android"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd"><g transform="translate(10.000000, 6.000000)"><path d="M22.5085098,45.4663212 C25.0369493,45.4663212 27.0867052,47.5686394 27.0867052,50.161916 L27.0867052,70.304735 C27.0867052,72.8980116 25.0369493,75 22.5085098,75 C19.9803918,75 17.9306358,72.8980116 17.9306358,70.304735 L17.9306358,50.161916 C17.9306358,47.5689691 19.9803918,45.466651 22.5085098,45.466651 L22.5085098,45.4663212 Z" fill="#43B1BD" fill-rule="nonzero"></path><path d="M12.2366216,20.984456 C12.2213844,21.1621825 12.2080925,21.3418838 12.2080925,21.5235599 L12.2080925,51.986222 C12.2080925,55.2623149 14.7624186,57.9015544 17.933362,57.9015544 L48.4478167,57.9015544 C51.6190843,57.9015544 54.1734104,55.2619858 54.1734104,51.986222 L54.1734104,21.5235599 C54.1734104,21.3418838 54.1669265,21.1615243 54.1520136,20.984456 L12.2366216,20.984456 Z" fill="#43B1BD" fill-rule="nonzero"></path><path d="M43.4088762,45.075412 C45.9573077,45.075412 48.0232707,47.1778944 48.0232707,49.7713737 L48.0232707,69.915767 C48.0232707,72.5092463 45.9573077,74.611399 43.4088762,74.611399 C40.8607688,74.611399 38.7948058,72.5092463 38.7948058,69.915767 L38.7948058,49.7713737 C38.7948058,47.1782241 40.8607688,45.0757417 43.4088762,45.0757417 L43.4088762,45.075412 Z M4.61439442,23.572451 C7.16250184,23.572451 9.22846482,25.6749335 9.22846482,28.2684128 L9.22846482,48.412806 C9.22846482,51.0062854 7.16250184,53.1087678 4.61439442,53.1087678 C2.06596298,53.1087678 0,51.0062854 0,48.4131358 L0,28.2687426 C-0.000324021798,25.6752632 2.06563896,23.572451 4.61439442,23.572451 Z M61.3859296,23.572451 C63.934037,23.572451 66,25.6749335 66,28.2684128 L66,48.412806 C66,51.0062854 63.934037,53.1087678 61.3859296,53.1087678 C58.8374982,53.1087678 56.7715352,51.0062854 56.7715352,48.4131358 L56.7715352,28.2687426 C56.7715352,25.6752632 58.8374982,23.572451 61.3859296,23.572451 Z M12.1916442,18.8514283 C12.3199568,9.47665177 20.3602337,1.79019235 30.680976,0.777202073 L35.318376,0.777202073 C45.6397663,1.7905221 53.6793952,9.47731127 53.8077078,18.8514283 L12.1916442,18.8514283 Z" fill="#43B1BD" fill-rule="nonzero"></path><path d="M13.734104,0 L18.6173398,8.5492228 M52.265896,0 L47.3823352,8.5492228" stroke="#4CA5AF" stroke-width="1.15363043" stroke-linecap="round" stroke-linejoin="round"></path><path d="M26.8835255,10.4922288 C26.887399,11.7762878 25.8257342,12.8202724 24.5119683,12.8238267 C23.198848,12.8270578 22.13105,11.7892124 22.1271765,10.5051534 L22.1271765,10.4922288 C22.1236258,9.20784661 23.1852907,8.16418513 24.4987338,8.16063087 C25.8118541,8.1570766 26.879652,9.19459889 26.8835255,10.4793042 L26.8835255,10.4922288 Z M44.6358276,10.4922288 C44.6397011,11.7762878 43.5780362,12.8202724 42.2642703,12.8238267 C40.95115,12.8270578 39.8833521,11.7892124 39.8794786,10.5051534 L39.8794786,10.4922288 C39.8759278,9.20784661 40.9375927,8.16418513 42.2510358,8.16063087 C43.5641561,8.1570766 44.6319541,9.19459889 44.6358276,10.4793042 L44.6358276,10.4922288 Z" fill="#FFFFFF" fill-rule="nonzero"></path></g></g></svg></div>
                <div class="technology-logo logo-cpp"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="none" stroke-width="1" fill="none" fill-rule="nonzero"><g transform="translate(9.000000, 5.000000)"><path d="M66.8871992,22.0625 C66.8866758,20.8046875 66.6163203,19.6932292 66.0695898,18.7393229 C65.532543,17.8013021 64.7282812,17.0151042 63.6494766,16.3934896 C54.7444961,11.284375 45.8308789,6.19088542 36.9287773,1.0765625 C34.5288164,-0.302083333 32.201875,-0.251822917 29.8197109,1.14661458 C26.2752539,3.2265625 8.52941406,13.3429687 3.24138672,16.390625 C1.063625,17.6450521 0.00392578125,19.5648438 0.00340234375,22.0601563 C0,32.3348958 0.00340234375,42.609375 0,52.884375 C0.0005234375,54.1145833 0.259363281,55.2044271 0.782015625,56.1440104 C1.31932422,57.1104167 2.13483984,57.9182292 3.23850781,58.5539063 C8.52679687,61.6015625 26.2749922,71.7171875 29.8186641,73.7976562 C32.201875,75.196875 34.5288164,75.246875 36.9295625,73.8677083 C45.8319258,68.753125 54.7460664,63.6598958 63.6523555,58.5507812 C64.7560234,57.9153646 65.5715391,57.1070312 66.1088477,56.1414063 C66.6307148,55.2018229 66.8900781,54.1119792 66.8906016,52.8815104 C66.8906016,52.8815104 66.8906016,32.3375 66.8871992,22.0625" fill="#4AA5B1"></path><path d="M33.5476328,37.3721354 L0.782015625,56.1440104 C1.31932422,57.1104167 2.13483984,57.9182292 3.23850781,58.5539062 C8.52679687,61.6015625 26.2749922,71.7171875 29.8186641,73.7976562 C32.201875,75.196875 34.5288164,75.246875 36.9295625,73.8677083 C45.8319258,68.753125 54.7460664,63.6598958 63.6523555,58.5507812 C64.7560234,57.9153646 65.5715391,57.1070312 66.1088477,56.1414063 L33.5476328,37.3721354" fill="#00515A"></path><path d="M23.8428398,42.9325521 C25.7494609,46.2445313 29.3336992,48.4783854 33.4453008,48.4783854 C37.5822891,48.4783854 41.1869414,46.2161458 43.0838789,42.86875 L33.5476328,37.3721354 L23.8428398,42.9325521" fill="#00515A"></path><path d="M66.8871992,22.0625 C66.8866758,20.8046875 66.6163203,19.6932292 66.0695898,18.7393229 L33.5476328,37.3721354 L66.1088477,56.1414063 C66.6307148,55.2018229 66.8900781,54.1119792 66.8906016,52.8815104 C66.8906016,52.8815104 66.8906016,32.3375 66.8871992,22.0625" fill="#00676A"></path><polyline fill="#FFFFFF" points="65.0967812 38.7138021 62.5523516 38.7138021 62.5523516 41.2460938 60.0073984 41.2460938 60.0073984 38.7138021 57.4632305 38.7138021 57.4632305 36.1822917 60.0073984 36.1822917 60.0073984 33.6505208 62.5523516 33.6505208 62.5523516 36.1822917 65.0967812 36.1822917 65.0967812 38.7138021"></polyline><polyline fill="#FFFFFF" points="55.8123086 38.7138021 53.2681406 38.7138021 53.2681406 41.2460938 50.7237109 41.2460938 50.7237109 38.7138021 48.1792812 38.7138021 48.1792812 36.1822917 50.7237109 36.1822917 50.7237109 33.6505208 53.2681406 33.6505208 53.2681406 36.1822917 55.8123086 36.1822917 55.8123086 38.7138021"></polyline><path d="M43.0838789,42.86875 C41.1869414,46.2161458 37.5822891,48.4783854 33.4453008,48.4783854 C29.3336992,48.4783854 25.7494609,46.2445312 23.8428398,42.9325521 C22.9166172,41.3231771 22.3840195,39.4598958 22.3840195,37.4721354 C22.3840195,31.39375 27.3365234,26.4661458 33.4453008,26.4661458 C37.5304687,26.4661458 41.0958633,28.6721354 43.0119062,31.9502604 L52.6800586,26.4106771 C48.8372422,19.8101562 41.6627461,15.3695312 33.4453008,15.3695312 C21.1769727,15.3695312 11.2319219,25.2653646 11.2319219,37.4721354 C11.2319219,41.4768229 12.3026133,45.2322917 14.1739023,48.4716146 C18.0070352,55.1070312 25.2019453,59.575 33.4453008,59.575 C41.7038359,59.575 48.91,55.0890625 52.7376367,48.4341146 L43.0838789,42.86875" fill="#FFFFFF"></path></g></g></svg></div>
                <div class="technology-logo logo-kotlin"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient x1="31.1709632%" y1="137.445556%" x2="73.8446667%" y2="52.3813953%" id="linearGradient-1"><stop stop-color="#00C4DA" offset="0%"></stop><stop stop-color="#4CA5AF" offset="100%"></stop></linearGradient><linearGradient x1="20.4548666%" y1="31.1119907%" x2="50%" y2="0%" id="linearGradient-2"><stop stop-color="#5AC6D5" offset="0%"></stop><stop stop-color="#3C8B94" offset="100%"></stop></linearGradient><linearGradient x1="-6.83271833%" y1="81.9362183%" x2="79.2773438%" y2="-5.55989583%" id="linearGradient-3"><stop stop-color="#377078" offset="0%"></stop><stop stop-color="#4CA5AF" offset="61.0271843%"></stop><stop stop-color="#5CD2DF" offset="100%"></stop></linearGradient></defs><g stroke="none" stroke-width="1" fill="none" fill-rule="nonzero"><g transform="translate(8.000000, 8.000000)"><polygon fill="url(#linearGradient-1)" points="0 70 35.5737705 34.4262295 70 70"></polygon><polygon fill="url(#linearGradient-2)" points="0 0 35 0 0 37.8"></polygon><polygon fill="url(#linearGradient-3)" points="35.1166667 0 0 36.9833333 0 70 35.1166667 34.8833333 70 0"></polygon></g></g></svg></div>
                <div class="technology-logo logo-scala"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"><defs><rect id="path-1" x="0" y="0" width="56" height="85"></rect><linearGradient x1="0%" y1="0%" x2="100%" y2="0%" id="linearGradient-4"><stop stop-color="#434343" offset="0%"></stop><stop stop-color="#4B4B4B" offset="100%"></stop></linearGradient><linearGradient x1="0%" y1="0%" x2="100%" y2="0%" id="linearGradient-5"><stop stop-color="#A00000" offset="0%"></stop><stop stop-color="#FF0000" offset="100%"></stop></linearGradient><linearGradient x1="0%" y1="0%" x2="100%" y2="0%" id="linearGradient-6"><stop stop-color="#39737B" offset="0%"></stop><stop stop-color="#5CD2DF" offset="100%"></stop></linearGradient></defs><g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd"><g transform="translate(15.000000, -1.000000)"><mask id="mask-2" fill="white"><use xlink:href="#path-1"></use></mask><g mask="url(#mask-2)"><g transform="translate(6.363636, -1.268657)"><g><path d="M0,11.840796 C0,11.840796 44.5454545,7.40049751 44.5454545,0 L44.5454545,17.761194 C44.5454545,17.761194 44.5454545,25.1616915 0,29.60199 L0,47.3631841 L0,11.840796 Z"  fill="url(#linearGradient-4)" fill-rule="nonzero" transform="translate(22.272727, 23.681592) scale(-1, 1) rotate(-180.000000) translate(-22.272727, -23.681592) "></path><path d="M0,35.5223881 C0,35.5223881 44.5454545,31.0820896 44.5454545,23.681592 L44.5454545,41.4427861 C44.5454545,41.4427861 44.5454545,48.8432836 0,53.2835821 L0,71.0447761 L0,35.5223881 Z" fill="url(#linearGradient-4)" fill-rule="nonzero" transform="translate(22.272727, 47.363184) scale(-1, 1) rotate(-180.000000) translate(-22.272727, -47.363184) "></path></g><g transform="translate(0.000000, 5.074627)"><path d="M0,12.0522388 C0,12.0522388 44.5454545,7.53264925 44.5454545,0 L44.5454545,18.0783582 C44.5454545,18.0783582 44.5454545,25.6110075 0,30.130597 L0,48.2089552 L0,12.0522388 Z" fill="url(#linearGradient-6)" fill-rule="nonzero"></path><path d="M0,36.1567164 C0,36.1567164 44.5454545,31.6371269 44.5454545,24.1044776 L44.5454545,42.1828358 C44.5454545,42.1828358 44.5454545,49.7154851 0,54.2350746 L0,72.3134328 L0,36.1567164 Z" fill="url(#linearGradient-6)" fill-rule="nonzero"></path><path d="M0,60.261194 C0,60.261194 44.5454545,55.7416045 44.5454545,48.2089552 L44.5454545,66.2873134 C44.5454545,66.2873134 44.5454545,73.8199627 0,78.3395522 L0,96.4179104 L0,60.261194 Z" fill="url(#linearGradient-6)" fill-rule="nonzero"></path></g></g></g></g></g></svg></div>
                <div class="technology-logo logo-javascript"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient x1="100%" y1="0%" x2="0%" y2="100%" id="linearGradient-7"><stop stop-color="#3ECFE0" offset="0%"></stop><stop stop-color="#4CA5AF" offset="100%"></stop></linearGradient></defs><g stroke="none" stroke-width="1" fill="none" fill-rule="nonzero"><g transform="translate(5.000000, 6.000000)"><rect fill="url(#linearGradient-7)" x="0" y="0" width="74" height="74"></rect><path d="M49.6936995,56.9169364 C51.1899405,59.3175189 53.1365866,61.0820396 56.5794737,61.0820396 C59.4717347,61.0820396 61.3193389,59.6616178 61.3193389,57.6989795 C61.3193389,55.3470575 59.4210347,54.5140369 56.2375431,53.1457512 L54.4925182,52.4100515 C49.4555271,50.3014318 46.1093238,47.6598643 46.1093238,42.0754978 C46.1093238,36.9313926 50.0981208,33.0153846 56.3318688,33.0153846 C60.7698918,33.0153846 63.9604578,34.5331274 66.2596461,38.5070645 L60.8241291,41.9364679 C59.6273721,39.8278482 58.3362894,38.9971448 56.3318688,38.9971448 C54.2873598,38.9971448 52.9915609,40.2715852 52.9915609,41.9364679 C52.9915609,43.99411 54.2885389,44.8271307 57.2833791,46.1015711 L59.028404,46.8361123 C64.9591309,49.3351742 68.3076923,51.8828966 68.3076923,57.6109272 C68.3076923,63.7861706 63.3709223,67.1692308 56.7410064,67.1692308 C50.2584744,67.1692308 46.0704145,64.1337453 44.0211892,60.1551738 L49.6936995,56.9169364 Z M25.035789,57.5112891 C26.132325,59.4229498 27.129819,61.0391721 29.5280493,61.0391721 C31.8213422,61.0391721 33.2680623,60.157491 33.2680623,56.7292461 L33.2680623,33.4069854 L40.2481622,33.4069854 L40.2481622,56.8219327 C40.2481622,63.9240419 36.0105813,67.1564864 29.8251751,67.1564864 C24.2363789,67.1564864 20.9998292,64.3144841 19.3538462,60.8914528 L25.035789,57.5112891 Z" fill="#FFFFFF"></path></g></g></svg></div>
            </div>

            <p>Read about <a href="https://gradle.org/features/">Gradle features</a> to learn what is possible with Gradle.</p>

            <h2>New projects with Gradle</h2>

            <p>Getting started with Gradle is easy! First, follow our guide to <a href="/userguide/installation.html">download and install Gradle</a>, then check out Gradle <a href="https://gradle.org/guides/#getting-started">getting started guides</a> to create your first build.</p>

            <p>If you're currently using Maven, see a visual <a href="https://gradle.org/maven-vs-gradle/">Gradle vs Maven comparison</a> and follow the guide for <a href="https://guides.gradle.org/migrating-from-maven/">migrating from Maven to Gradle</a>.</p>

            <h2>Using existing Gradle builds</h2>

            <p>Gradle supports many major IDEs, including Android Studio, Eclipse, IntelliJ IDEA, Visual Studio 2017, and XCode.</p>

            <p>You can also invoke Gradle via its <a href="/userguide/command_line_interface.html">command line interface</a>
                in your terminal or through your continuous integration server.</p>

            <p><a href="https://scans.gradle.com/get-started" title="Get started with build scans">Gradle build scans</a> help you understand build results, improve build performance, and collaborate to fix problems faster.</p>

            <h2>Getting help</h2>

            <p>If you ever run into trouble, there are a number of ways to get help:</p>

            <ul>
                <li><strong><a href="https://discuss.gradle.org" title="Gradle help and discussion forums">Forum</a></strong> — Community members and core contributors answer your questions.</li>
                <li><strong><a href="https://gradle.org/training/" title="Gradle training schedule">Training</a></strong> — Free, web-based Gradle training from Gradle developers happens every month.</li>
                <li><strong><a href="https://gradle.org/services/">Enterprise Services</a></strong> — Support and training can be purchased alongside a <a href="https://gradle.com/enterprise">Gradle Enterprise</a> subscription.</li>
            </ul>

            <p>We hope you build happiness with Gradle!</p>

            <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/legalnotice"/>
        </main>
    </xsl:template>
</xsl:stylesheet>
