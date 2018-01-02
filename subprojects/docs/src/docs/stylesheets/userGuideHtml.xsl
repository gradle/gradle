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
                <li><p><strong>Highly customizable</strong> — Gradle is modeled in a way that customizable and extensible in the most fundamental ways.</p></li>
                <li><p><strong>Fast</strong> — Gradle completes tasks quickly by reusing outputs from previous executions, processing only inputs that changed, and executing tasks in parallel.</p></li>
                <li><p><strong>Powerful</strong> — Gradle is the official build tool for Android, and comes with support for many popular languages and technologies.</p></li>
            </ul>

            <div class="technology-logos">
                <div class="technology-logo logo-java"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd"><g fill-rule="nonzero"><g transform="translate(12.000000, 2.000000)"><path d="M19.183327,61.4235105 C19.183327,61.4235105 16.0927127,63.3086723 21.3828119,63.9465864 C27.7916185,64.7134551 31.0670854,64.6034778 38.1296339,63.2014387 C38.1296339,63.2014387 39.9864439,64.4226212 42.5796127,65.4803239 C26.7472446,72.5975243 6.74784437,65.0680805 19.183327,61.4235105" fill="#4CA5AF"></path><path d="M17.1523256,52.7665056 C17.1523256,52.7665056 13.6984279,55.3506468 18.9733216,55.9021176 C25.7946719,56.6134095 31.1816403,56.6715862 40.5031676,54.8573527 C40.5031676,54.8573527 41.7924606,56.1785116 43.8197956,56.9009998 C24.746687,62.5382079 3.5026748,57.3455572 17.1523256,52.7665056" fill="#4CA5AF"></path><path d="M33.2898906,38.5120507 C37.1335976,42.9771464 32.2799939,46.9951691 32.2799939,46.9951691 C32.2799939,46.9951691 42.0398225,41.9115827 37.5575738,35.5457223 C33.3712923,29.6091648 30.1609722,26.6595231 47.5403443,16.489533 C47.5403443,16.489533 20.2604625,23.364012 33.2898906,38.5120507" fill="#1C8789"></path><path d="M54.2563689,67.8505555 C54.2563689,67.8505555 56.532228,69.6570016 51.7499304,71.0545159 C42.656255,73.7082886 13.901083,74.5096443 5.91301706,71.1602489 C3.04151277,69.956857 8.42639675,68.2868608 10.1202763,67.9364374 C11.8868219,67.5674168 12.8963385,67.6361641 12.8963385,67.6361641 C9.7029342,65.4690557 -7.74452373,71.8913946 4.0338932,73.7306471 C36.1553498,78.748785 62.5882431,71.4709701 54.2563689,67.8505555" fill="#4CA5AF"></path><path d="M20.7745986,43.7109862 C20.7745986,43.7109862 6.11812587,47.3506463 15.5843758,48.672341 C19.581319,49.2318342 27.5491228,49.1052551 34.9709305,48.4550886 C41.0364423,47.9201385 47.12695,46.7827448 47.12695,46.7827448 C47.12695,46.7827448 44.9881756,47.7403826 43.4408246,48.8450521 C28.5574331,52.9376245 -0.19451871,51.0337114 8.08279875,46.8475114 C15.0829376,43.3096601 20.7745986,43.7109862 20.7745986,43.7109862" fill="#4CA5AF"></path><path d="M47.0176174,58.5589978 C62.1463733,50.7993448 55.1514319,43.3423894 50.2690567,44.3470189 C49.0723706,44.5928667 48.5387955,44.805892 48.5387955,44.805892 C48.5387955,44.805892 48.9830429,44.1189768 49.8315469,43.8216425 C59.4904516,40.469874 66.9187733,53.7072571 46.7135557,58.9500805 C46.7135557,58.9502949 46.9476332,58.7437055 47.0176174,58.5589978" fill="#4CA5AF"></path><path d="M37.9619675,0 C37.9619675,0 46.2921791,8.27895025 30.0611238,21.0096369 C17.0454653,31.2218379 27.0931483,37.0445614 30.0557216,43.6972625 C22.4582661,36.8869816 16.8827507,30.8918649 20.6232426,25.3121663 C26.113403,17.1216669 41.323008,13.1506132 37.9619675,0" fill="#1C8789"></path><path d="M22.0590017,79.3158167 C36.4667145,80.2185961 58.5913894,78.8149292 59.1153846,72.1417069 C59.1153846,72.1417069 58.1081493,74.6714735 47.2081857,76.6805113 C34.9109015,78.9457974 19.7439347,78.681317 10.7482517,77.2295244 C10.7484674,77.2293134 12.5897824,78.7214218 22.0590017,79.3158167" fill="#4CA5AF"></path></g></g></g></svg></div>
                <div class="technology-logo logo-android"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd"><g transform="translate(10.000000, 6.000000)"><path d="M22.5085098,45.4663212 C25.0369493,45.4663212 27.0867052,47.5686394 27.0867052,50.161916 L27.0867052,70.304735 C27.0867052,72.8980116 25.0369493,75 22.5085098,75 C19.9803918,75 17.9306358,72.8980116 17.9306358,70.304735 L17.9306358,50.161916 C17.9306358,47.5689691 19.9803918,45.466651 22.5085098,45.466651 L22.5085098,45.4663212 Z" fill="#43B1BD" fill-rule="nonzero"></path><path d="M12.2366216,20.984456 C12.2213844,21.1621825 12.2080925,21.3418838 12.2080925,21.5235599 L12.2080925,51.986222 C12.2080925,55.2623149 14.7624186,57.9015544 17.933362,57.9015544 L48.4478167,57.9015544 C51.6190843,57.9015544 54.1734104,55.2619858 54.1734104,51.986222 L54.1734104,21.5235599 C54.1734104,21.3418838 54.1669265,21.1615243 54.1520136,20.984456 L12.2366216,20.984456 Z" fill="#43B1BD" fill-rule="nonzero"></path><path d="M43.4088762,45.075412 C45.9573077,45.075412 48.0232707,47.1778944 48.0232707,49.7713737 L48.0232707,69.915767 C48.0232707,72.5092463 45.9573077,74.611399 43.4088762,74.611399 C40.8607688,74.611399 38.7948058,72.5092463 38.7948058,69.915767 L38.7948058,49.7713737 C38.7948058,47.1782241 40.8607688,45.0757417 43.4088762,45.0757417 L43.4088762,45.075412 Z M4.61439442,23.572451 C7.16250184,23.572451 9.22846482,25.6749335 9.22846482,28.2684128 L9.22846482,48.412806 C9.22846482,51.0062854 7.16250184,53.1087678 4.61439442,53.1087678 C2.06596298,53.1087678 0,51.0062854 0,48.4131358 L0,28.2687426 C-0.000324021798,25.6752632 2.06563896,23.572451 4.61439442,23.572451 Z M61.3859296,23.572451 C63.934037,23.572451 66,25.6749335 66,28.2684128 L66,48.412806 C66,51.0062854 63.934037,53.1087678 61.3859296,53.1087678 C58.8374982,53.1087678 56.7715352,51.0062854 56.7715352,48.4131358 L56.7715352,28.2687426 C56.7715352,25.6752632 58.8374982,23.572451 61.3859296,23.572451 Z M12.1916442,18.8514283 C12.3199568,9.47665177 20.3602337,1.79019235 30.680976,0.777202073 L35.318376,0.777202073 C45.6397663,1.7905221 53.6793952,9.47731127 53.8077078,18.8514283 L12.1916442,18.8514283 Z" fill="#43B1BD" fill-rule="nonzero"></path><path d="M13.734104,0 L18.6173398,8.5492228 M52.265896,0 L47.3823352,8.5492228" stroke="#4CA5AF" stroke-width="1.15363043" stroke-linecap="round" stroke-linejoin="round"></path><path d="M26.8835255,10.4922288 C26.887399,11.7762878 25.8257342,12.8202724 24.5119683,12.8238267 C23.198848,12.8270578 22.13105,11.7892124 22.1271765,10.5051534 L22.1271765,10.4922288 C22.1236258,9.20784661 23.1852907,8.16418513 24.4987338,8.16063087 C25.8118541,8.1570766 26.879652,9.19459889 26.8835255,10.4793042 L26.8835255,10.4922288 Z M44.6358276,10.4922288 C44.6397011,11.7762878 43.5780362,12.8202724 42.2642703,12.8238267 C40.95115,12.8270578 39.8833521,11.7892124 39.8794786,10.5051534 L39.8794786,10.4922288 C39.8759278,9.20784661 40.9375927,8.16418513 42.2510358,8.16063087 C43.5641561,8.1570766 44.6319541,9.19459889 44.6358276,10.4793042 L44.6358276,10.4922288 Z" fill="#FFFFFF" fill-rule="nonzero"></path></g></g></svg></div>
                <div class="technology-logo logo-cpp"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="none" stroke-width="1" fill="none" fill-rule="nonzero"><g transform="translate(9.000000, 5.000000)"><path d="M66.8871992,22.0625 C66.8866758,20.8046875 66.6163203,19.6932292 66.0695898,18.7393229 C65.532543,17.8013021 64.7282812,17.0151042 63.6494766,16.3934896 C54.7444961,11.284375 45.8308789,6.19088542 36.9287773,1.0765625 C34.5288164,-0.302083333 32.201875,-0.251822917 29.8197109,1.14661458 C26.2752539,3.2265625 8.52941406,13.3429687 3.24138672,16.390625 C1.063625,17.6450521 0.00392578125,19.5648438 0.00340234375,22.0601563 C0,32.3348958 0.00340234375,42.609375 0,52.884375 C0.0005234375,54.1145833 0.259363281,55.2044271 0.782015625,56.1440104 C1.31932422,57.1104167 2.13483984,57.9182292 3.23850781,58.5539063 C8.52679687,61.6015625 26.2749922,71.7171875 29.8186641,73.7976562 C32.201875,75.196875 34.5288164,75.246875 36.9295625,73.8677083 C45.8319258,68.753125 54.7460664,63.6598958 63.6523555,58.5507812 C64.7560234,57.9153646 65.5715391,57.1070312 66.1088477,56.1414063 C66.6307148,55.2018229 66.8900781,54.1119792 66.8906016,52.8815104 C66.8906016,52.8815104 66.8906016,32.3375 66.8871992,22.0625" fill="#4AA5B1"></path><path d="M33.5476328,37.3721354 L0.782015625,56.1440104 C1.31932422,57.1104167 2.13483984,57.9182292 3.23850781,58.5539062 C8.52679687,61.6015625 26.2749922,71.7171875 29.8186641,73.7976562 C32.201875,75.196875 34.5288164,75.246875 36.9295625,73.8677083 C45.8319258,68.753125 54.7460664,63.6598958 63.6523555,58.5507812 C64.7560234,57.9153646 65.5715391,57.1070312 66.1088477,56.1414063 L33.5476328,37.3721354" fill="#00515A"></path><path d="M23.8428398,42.9325521 C25.7494609,46.2445313 29.3336992,48.4783854 33.4453008,48.4783854 C37.5822891,48.4783854 41.1869414,46.2161458 43.0838789,42.86875 L33.5476328,37.3721354 L23.8428398,42.9325521" fill="#00515A"></path><path d="M66.8871992,22.0625 C66.8866758,20.8046875 66.6163203,19.6932292 66.0695898,18.7393229 L33.5476328,37.3721354 L66.1088477,56.1414063 C66.6307148,55.2018229 66.8900781,54.1119792 66.8906016,52.8815104 C66.8906016,52.8815104 66.8906016,32.3375 66.8871992,22.0625" fill="#00676A"></path><polyline fill="#FFFFFF" points="65.0967812 38.7138021 62.5523516 38.7138021 62.5523516 41.2460938 60.0073984 41.2460938 60.0073984 38.7138021 57.4632305 38.7138021 57.4632305 36.1822917 60.0073984 36.1822917 60.0073984 33.6505208 62.5523516 33.6505208 62.5523516 36.1822917 65.0967812 36.1822917 65.0967812 38.7138021"></polyline><polyline fill="#FFFFFF" points="55.8123086 38.7138021 53.2681406 38.7138021 53.2681406 41.2460938 50.7237109 41.2460938 50.7237109 38.7138021 48.1792812 38.7138021 48.1792812 36.1822917 50.7237109 36.1822917 50.7237109 33.6505208 53.2681406 33.6505208 53.2681406 36.1822917 55.8123086 36.1822917 55.8123086 38.7138021"></polyline><path d="M43.0838789,42.86875 C41.1869414,46.2161458 37.5822891,48.4783854 33.4453008,48.4783854 C29.3336992,48.4783854 25.7494609,46.2445312 23.8428398,42.9325521 C22.9166172,41.3231771 22.3840195,39.4598958 22.3840195,37.4721354 C22.3840195,31.39375 27.3365234,26.4661458 33.4453008,26.4661458 C37.5304687,26.4661458 41.0958633,28.6721354 43.0119062,31.9502604 L52.6800586,26.4106771 C48.8372422,19.8101562 41.6627461,15.3695312 33.4453008,15.3695312 C21.1769727,15.3695312 11.2319219,25.2653646 11.2319219,37.4721354 C11.2319219,41.4768229 12.3026133,45.2322917 14.1739023,48.4716146 C18.0070352,55.1070312 25.2019453,59.575 33.4453008,59.575 C41.7038359,59.575 48.91,55.0890625 52.7376367,48.4341146 L43.0838789,42.86875" fill="#FFFFFF"></path></g></g></svg></div>
                <div class="technology-logo logo-kotlin"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient x1="31.1709632%" y1="137.445556%" x2="73.8446667%" y2="52.3813953%" id="linearGradient-1"><stop stop-color="#00C4DA" offset="0%"></stop><stop stop-color="#4CA5AF" offset="100%"></stop></linearGradient><linearGradient x1="20.4548666%" y1="31.1119907%" x2="50%" y2="0%" id="linearGradient-2"><stop stop-color="#5AC6D5" offset="0%"></stop><stop stop-color="#3C8B94" offset="100%"></stop></linearGradient><linearGradient x1="-6.83271833%" y1="81.9362183%" x2="79.2773438%" y2="-5.55989583%" id="linearGradient-3"><stop stop-color="#377078" offset="0%"></stop><stop stop-color="#4CA5AF" offset="61.0271843%"></stop><stop stop-color="#5CD2DF" offset="100%"></stop></linearGradient></defs><g stroke="none" stroke-width="1" fill="none" fill-rule="nonzero"><g transform="translate(8.000000, 8.000000)"><polygon fill="url(#linearGradient-1)" points="0 70 35.5737705 34.4262295 70 70"></polygon><polygon fill="url(#linearGradient-2)" points="0 0 35 0 0 37.8"></polygon><polygon fill="url(#linearGradient-3)" points="35.1166667 0 0 36.9833333 0 70 35.1166667 34.8833333 70 0"></polygon></g></g></svg></div>
                <div class="technology-logo logo-groovy"><svg width="125px" height="85px" viewBox="0 0 125 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient x1="-91.3378645%" y1="159.9507%" x2="198.862124%" y2="12.7805149%" id="linearGradient-1"><stop stop-color="#61D9E1" offset="0%"></stop><stop stop-color="#32B1BF" offset="100%"></stop></linearGradient></defs>
                    <g stroke="none" stroke-width="1" fill="none" fill-rule="nonzero"><g transform="translate(1.000000, 12.000000)"><path d="M30.1785073,51.8137816 C32.7573755,47.7707295 34.5929041,44.3673145 34.257469,44.250643 C33.922032,44.1339734 32.7613807,44.3442852 31.6782444,44.718002 C28.4161846,45.8435196 27.7458969,45.5451897 25.3094625,41.8833894 C22.673232,37.9213158 22.5788314,37.5124091 23.9533106,36.0091798 C24.851121,35.0272809 24.7971268,34.7983937 23.3237197,33.3400737 C22.4373385,32.4627669 21.7121177,31.4807852 21.7121177,31.1578913 C21.7121177,30.6147847 15.6748223,28.1521054 6.12974838,24.8016728 C3.9655289,24.0420054 2.19480519,23.2921057 2.19480519,23.1352294 C2.19480519,22.9783472 6.76702688,22.9415011 12.3552969,23.0533447 L22.5157924,23.2566942 L23.5075982,21.7084243 C27.6991125,15.165224 34.0289333,9.25514156 36.8453353,9.25514156 C37.4318158,9.25514156 38.6222333,9.80834947 39.4906982,10.4844904 C40.9048788,11.5854927 41.0991073,12.2677234 41.3510762,17.0192161 C41.5058099,19.9371749 41.8233268,22.5135448 42.0566567,22.7444893 C42.2899846,22.97543 43.0302327,22.67233 43.7016521,22.0709272 C44.5590677,21.3029188 45.4616859,21.0947028 46.7343034,21.371353 C48.0526593,21.6579484 48.8045177,21.4617715 49.4945723,20.6511231 C50.5296892,19.4351331 55.710019,11.2155189 55.710019,10.7891103 C55.710019,10.6441963 56.7356859,8.90779969 57.9892846,6.93045191 C59.2428736,4.95310412 60.686682,2.67307534 61.1977398,1.86372414 C62.106619,0.424355317 62.2490255,0.594696801 67.7094775,9.65302291 C73.3473755,19.0057114 76.3817508,22.456901 77.9008476,21.2443314 C78.9609775,20.3981075 84.5243314,20.3500881 86.0792489,21.1737358 C86.9630119,21.6418661 87.5245398,21.6357289 88.0126593,21.1525999 C89.0256599,20.149974 91.4509359,20.3246005 92.0848905,21.4458222 C92.5843573,22.3291756 92.7117424,22.3291756 93.4524502,21.4458222 C93.9016827,20.9100656 95.2322275,20.4717284 96.4092093,20.4717284 C98.0726197,20.4717284 98.7066171,20.8137105 99.2560982,22.0073422 L99.9630093,23.5429541 L110.890028,23.1600711 C116.899889,22.9494796 121.817049,22.9119471 121.817049,23.0766709 C121.817049,23.2413831 117.354826,25.0186482 111.900995,27.0261444 C106.447166,29.0336329 101.894437,30.7364057 101.783818,30.8100728 C101.673198,30.8837399 101.919884,32.6403704 102.33201,34.7136902 C103.404329,40.1083667 102.70498,43.7036493 100.008707,46.6575345 C98.7991612,47.9826494 96.8366197,49.3846359 95.6474995,49.7730603 C94.4583794,50.1614847 93.4854658,50.618754 93.4854658,50.7892081 C93.4854658,50.9596641 94.7546703,53.0410279 96.3059236,55.4144591 C97.8571749,57.787896 98.893482,59.7297981 98.6088171,59.7297981 C98.1441989,59.7297981 86.8289723,55.4026726 69.2343405,48.4965259 C65.7650547,47.1347866 62.6057372,46.0206295 62.2136372,46.0206295 C61.4824106,46.0206295 55.1681372,48.3896183 36.6504755,55.6114188 C25.9117905,59.7994605 25.4896632,59.9445408 25.4896632,59.4472884 C25.4896632,59.291914 27.5996431,55.8568318 30.1785073,51.8137816 Z M47.5253411,49.5652473 C53.2388781,47.3681605 58.9051924,45.2210734 60.1171554,44.7939462 C62.2017034,44.0593017 62.8988138,44.2434858 73.0237671,48.2040575 C78.9104392,50.5067427 84.7184138,52.7497318 85.9303749,53.1884662 C87.1423379,53.6272102 89.6400833,54.5806715 91.4809106,55.3072757 C93.5799424,56.13579 94.6528281,56.3352755 94.3583937,55.8422939 C91.0693612,50.3354094 90.9688223,50.2436258 88.6894008,50.666866 C85.9399963,51.1773741 84.2324586,50.6536585 83.3549158,49.0307651 C82.7858664,47.9783748 82.9595703,47.4636808 84.3785041,45.9977968 C86.4919203,43.8144556 86.1599138,41.8058199 83.4348112,40.2884883 C82.3891671,39.7062798 81.0697612,38.2671175 80.5027944,37.090347 L79.4719456,34.9507641 L77.2617814,36.4352658 C74.5453047,38.2598389 71.2324398,38.3538513 68.4667866,36.6848551 L66.4281736,35.454599 L64.0596521,37.047761 C61.6879729,38.6430477 58.9574937,38.8491505 56.1822132,37.642373 C55.4176067,37.3098926 55.0804281,37.4391219 55.0804281,38.0646434 C55.0804281,38.5603937 54.3588132,39.2914288 53.4768288,39.6891736 C50.8325041,40.8816658 48.251945,40.5650094 46.5023567,38.8333362 L44.9070112,37.2543266 L43.619384,38.8745186 C42.9111911,39.765623 41.2720749,41.1568121 39.9769067,41.9660376 C38.3737281,42.9677227 36.3890995,45.4119114 33.7594262,49.6232369 C31.6349801,53.0254662 29.8967976,55.9822068 29.8967976,56.1937854 C29.8967976,56.4053564 31.5258658,55.8992945 33.5169444,55.0692011 C35.5080268,54.2391116 41.8118041,51.762334 47.5253411,49.5652473 Z M95.9865015,47.201688 C100.322002,44.7899512 101.326938,40.3283654 99.4105632,31.9998984 C97.8089918,25.0395161 97.0094918,22.7617189 96.0608995,22.4565578 C95.4045969,22.2454245 95.3037424,22.4771866 95.6735138,23.3466383 C98.072571,28.9878308 98.2059093,31.8200062 96.1099203,32.6160801 C94.6700158,33.1629716 93.1425716,31.113942 91.9412801,27.0239483 C91.0110619,23.8568688 90.0383041,22.4295894 89.3048119,23.1555709 C89.1291814,23.3294088 89.4369833,24.3357965 89.9888197,25.3920044 C90.5406502,26.4482008 91.2973671,29.6289275 91.6704112,32.4602816 C92.0434573,35.2916299 92.7640184,38.2563047 93.2716573,39.0484452 C94.5908742,41.1070073 96.6907379,40.610904 98.0490249,37.9197636 C99.3919593,35.2590275 99.543734,37.0313297 98.2378495,40.1247479 C96.8818612,43.3368486 92.854521,44.3000217 88.1889833,42.5280184 C87.4422366,42.2444019 87.1895567,42.5379482 87.1895567,43.6889244 C87.1895567,44.5361933 86.6175736,45.9491353 85.9184762,46.8287849 L84.6473956,48.4281592 L86.3906703,48.7195401 C89.4003034,49.2226 93.5270236,48.5698382 95.9865015,47.201688 Z M33.5774431,41.8922028 C42.6280242,39.3173155 44.2453418,36.3140169 41.2389073,27.6651395 C40.5411444,25.6578303 39.9702521,23.4985614 39.9702521,22.8667543 C39.9702521,20.7155449 38.8770989,21.7095811 38.0578242,24.6057947 C36.9743119,28.4360921 33.7153924,31.6916812 30.9775982,31.6787841 C28.5999093,31.6675626 27.8251612,31.2391609 26.8430956,29.3924994 C25.0805995,26.0783152 27.0018015,20.9921392 31.9020969,15.9993606 C35.4912482,12.3424589 37.4518885,12.0112759 37.4518885,15.0619065 C37.4518885,19.8191575 34.1379911,26.8223225 32.3921956,25.754411 C31.5385495,25.2322301 31.6769918,22.9263597 32.6682775,21.1558642 C33.5112677,19.650242 33.3399931,17.3560116 32.3846021,17.3560116 C31.2767664,17.3560116 29.0835366,21.3494941 28.7937229,23.8943569 C28.5375249,26.1441195 28.6977112,26.6911594 29.7807073,27.2648225 C33.5759879,29.2751993 38.5479755,23.139556 38.7984015,16.136605 C38.9299028,12.4590963 38.210721,11.4241044 35.9193638,11.9933105 C32.5845346,12.821729 23.6008905,24.292042 23.6008905,27.7215269 C23.6008905,30.4040602 24.9620664,32.2906103 27.3583456,32.9292602 C31.4325385,34.0150954 35.3717333,32.0859285 38.1628846,27.6378896 L39.3359528,25.7684553 L39.3385242,28.308199 C39.3416801,31.9858271 36.9178379,33.9242663 29.2674521,36.3614927 C27.3629411,36.9682286 25.7252469,37.5288462 25.6281405,37.6073124 C25.2734859,37.8938904 28.8789307,42.9049166 29.4397846,42.9049166 C29.7578275,42.9049166 31.6198788,42.4491917 33.5776866,41.8922028 L33.5774431,41.8922028 Z M88.1067015,37.3264184 C89.0151697,36.3328468 89.1753073,35.2535074 88.9805567,31.4364002 C88.7687275,27.2845528 88.5368956,26.5508705 86.9036547,24.86359 C83.9525859,21.8148826 80.3073112,22.5926145 82.4410203,25.8157151 C83.5752853,27.5291001 84.9589554,27.2364659 84.4701911,25.3865556 C84.0808456,23.9129516 84.8072002,23.8742314 86.0731242,25.3010982 C88.4483859,27.9783427 87.8488398,31.6883263 85.0409294,31.6883263 C83.1187554,31.6883263 82.5197703,30.8723987 81.2668651,26.5473864 C80.695971,24.5766958 80.0007015,22.9643068 79.7218125,22.9643068 C78.7927885,22.9643068 78.3790456,23.7758035 78.9567716,24.4648349 C79.2705794,24.8390992 80.1396638,27.5288379 80.8880781,30.4420381 C82.7716554,37.7738408 85.4030119,40.2833634 88.1067015,37.3264184 Z M52.3071073,37.4428663 C52.6947268,37.1854643 52.8719937,35.9680726 52.7120392,34.661961 C52.4540216,32.5551173 52.3094508,32.4014319 51.1589158,33.010873 C49.5055398,33.8866702 47.7287099,32.2273858 47.1260158,29.2447835 C46.6687944,26.9820795 46.5288872,27.0265204 49.8583067,28.3768491 C50.2757508,28.5461521 51.3381853,27.9711721 52.2192736,27.0991058 C53.8311073,25.5037767 54.361745,23.5874509 53.1916554,23.5874509 C52.8453814,23.5874509 52.5620645,23.8678667 52.5620645,24.210595 C52.5620645,25.1070152 50.8395366,24.979874 50.4514184,24.0548099 C50.225008,23.5151739 49.7427034,23.7066116 48.8814541,24.677954 C48.1977171,25.4490956 47.3626106,26.0800293 47.0256599,26.0800293 C46.6887073,26.0800293 46.2336599,25.4490956 46.0144495,24.677954 C45.6303034,23.3266304 45.5832171,23.3181062 44.7136632,24.4423994 C43.951858,25.4273794 43.9450456,25.7411379 44.669908,26.4585432 C45.4375612,27.2183398 46.8957814,32.4107987 46.8957814,34.3844875 C46.8957814,36.8387082 50.3105859,38.7686734 52.3071385,37.4428663 L52.3071073,37.4428663 Z M63.7895275,34.18913 C66.1555671,31.8473178 66.8566418,28.3370569 65.5679879,25.2844548 C64.5608138,22.8986239 62.8125346,22.0056821 60.3474463,22.6180442 C58.5157651,23.0730596 55.7260612,28.3592822 55.7161729,31.3938816 C55.7007444,36.1233332 60.2454969,37.6968651 63.7895275,34.18913 Z M59.4875645,30.4420381 C58.7950125,29.7565776 58.2373924,28.4246083 58.2484047,27.4821026 C58.2665995,25.9242616 58.3444729,25.8676042 59.1048054,26.8589585 C60.0446703,28.0844019 61.7147995,28.2966805 62.3546255,27.2720202 C62.5872853,26.8994313 62.4623431,26.282513 62.0769892,25.901105 C61.0871216,24.9213734 61.2070651,23.5874509 62.2850255,23.5874509 C63.4833444,23.5874509 65.1538807,26.0004294 65.1538807,27.7313159 C65.1538807,29.0051644 62.5348418,31.6883263 61.2914314,31.6883263 C60.9918502,31.6883263 60.1801145,31.1274947 59.4875645,30.4420381 Z M76.3087924,34.0251139 C78.9212742,31.2095741 78.6666677,26.8722315 75.7052372,23.7432379 C74.1759054,22.1273705 70.7780333,21.8487749 69.3873444,23.2252256 C68.1040437,24.4953857 66.8228021,29.4902216 67.298795,31.3673046 C68.3139677,35.3706629 73.6105632,36.9330716 76.3087924,34.0251139 Z M70.9602892,29.4719875 C69.926608,28.7553804 69.5714431,27.9006324 69.6009268,26.2004818 C69.6339171,24.2993071 69.734232,24.127806 70.1778327,25.2144202 C70.8295982,26.8109525 72.1965833,27.4138747 73.3956521,26.6336787 C74.1393268,26.1497958 74.1046047,25.921057 73.1811638,25.2205574 C71.9496268,24.2863464 71.709395,22.9643068 72.7711671,22.9643068 C73.7205775,22.9643068 77.116106,26.6405582 77.116106,27.6684539 C77.116106,28.6716775 74.7049262,30.4420381 73.3385625,30.4420381 C72.8001112,30.4420381 71.7298807,30.0055114 70.9602892,29.4719875 Z M107.966053,26.7169439 L114.576757,24.2611518 L107.493859,24.2358454 C101.155053,24.2132172 100.411864,24.3251302 100.419557,25.301075 C100.427368,26.2897241 101.0684,29.2120327 101.271381,29.184214 C101.317593,29.1778705 104.330168,28.0676024 107.966053,26.7169188 L107.966053,26.7169439 Z M21.5712288,26.277471 L22.2468619,24.2939122 L16.3117593,24.0964666 C13.0474585,23.9878719 10.3772949,24.0709906 10.3780749,24.2811868 C10.3792047,24.5887445 20.6066275,28.543661 20.8316606,28.3235583 C20.8667937,28.2891609 21.199632,27.3684292 21.5712288,26.277471 Z M62.2678262,20.1293336 C63.6639307,20.3502944 65.2385684,20.9158537 65.7670262,21.3861397 C66.5876995,22.1164729 66.9446781,22.066695 68.2141827,21.0448941 C69.031669,20.3869284 70.0941034,19.8485881 70.5751495,19.8485881 C71.0561995,19.8485881 71.4497898,19.7110988 71.4497898,19.5430471 C71.4497898,18.9628458 62.3836456,4.27004492 62.0378801,4.28988705 C61.6317112,4.31319243 52.3393028,19.7316871 52.0532099,20.8570041 C51.9331437,21.3292972 52.309971,21.5036345 53.0415814,21.3142734 C53.6919112,21.1459499 54.5910931,21.4460131 55.0397508,21.9810814 C55.7674262,22.8488962 56.0646366,22.7797582 57.7924807,21.3407636 C59.4340008,19.9736585 60.1166625,19.7888708 62.2678262,20.1293336 Z" fill="#5D5D5D"></path><path d="M55.1080632,22.0415352 C54.735619,21.6696096 54.5875671,21.5614121 54.2779827,21.4348995 C53.8712469,21.2686892 53.4296255,21.2239205 53.015195,21.3068907 C52.6454392,21.3809164 52.542919,21.3888872 52.3757625,21.3565972 C52.0967547,21.3027009 51.5,21 51,20.3123435 C50.5,19.624687 50.0054429,19.0704179 51.5,16.5 C54.5357857,11.2788879 61.0491584,1.40214361 61.5,1 C61.5499305,0.955461941 62.0516234,0.955461941 62.1017818,1 C62.370161,1.2383091 65.9690041,6.03858781 68.1243599,9.5 C70.4171531,13.1821333 75.293059,18.9336159 75.5,20.5744366 C75.5562165,21.0201735 74.5,20.3123435 73,19.9865652 C71.5,19.6607868 71.5395773,19.9429322 71,19.9865652 C70.3294299,20.0407912 69.1848969,20.2813087 68.1243599,21.0964111 C67.6203755,21.4837635 67.4052833,21.6284388 67.1599534,21.7450987 C66.9555482,21.8422964 66.9384755,21.8462028 66.7180301,21.8462028 C66.5010794,21.8462028 66.4790801,21.8414018 66.3096736,21.7572632 C66.209908,21.707707 66.0165521,21.5742282 65.873008,21.4558194 C65.4486288,21.1057508 64.9381028,20.8511388 64.1057411,20.5744366 C63.0479547,20.2227966 61.9152463,20.0117385 60.9507716,19.9865652 C60.1892119,19.9666903 59.8380424,20.0343783 59.2606379,20.3123435 C58.7892327,20.5392776 58.5620255,20.7004922 57.4830892,21.5736035 C56.3856034,22.4617212 55.9958645,22.6460249 55.6004918,22.4638576 C55.5029431,22.4189115 55.3826918,22.3157811 55.1080632,22.0415352 Z" fill="url(#linearGradient-1)"></path><path d="M21.7545166,31.4224854 C21.7545166,31.9224854 2.78168016,23.9976948 0.987064556,22.9186832 C0.73936187,22.7697559 0.719346627,22.7471282 0.790036176,22.6959673 C1.20046908,22.3989277 5.26518616,22.2511637 9.85932967,22.3662767 C11.6915068,22.4121856 22.2545166,22.4694492 22.7545166,22.6959673 C23.2545166,22.9224854 21.8370492,25.5379744 21.5,26.8861711 C21,28.8861711 21.7545166,30.9224854 21.7545166,31.4224854 Z" fill="url(#linearGradient-1)"></path><path d="M100,24 C100,23.4694122 99.5693266,23.0522786 101.5,23 C102.474702,22.9736062 109.32285,22.9773525 112.5,23 L122.5,23 L113.5,26.5 C108.611769,28.3134595 102,31.5 101.5,31 C101,30.5 101,28.5 100.5,27 C100,25.5 100,25.5 100,24 Z" fill="url(#linearGradient-1)"></path><path d="M25.4187688,59.4687707 C25.4187688,59.3201563 26.0555675,58.2091207 27.6695383,55.541804 C31.5366513,49.1508488 31.482168,45.9587183 36.03125,43.0151716 C40.580332,40.071625 43.061395,39.5870094 44.2190444,38.1407517 L44.9328281,37.2490187 L45.8951463,38.1854066 C47.5797905,39.8246599 48.5953995,40.2998971 50.433556,40.3090826 C52.681721,40.3203235 55.1436515,39.0773902 55.1436515,37.9311532 C55.1436515,37.6728257 55.3295034,37.4062537 55.5096067,37.4062537 C55.5858379,37.4062537 56.0193846,37.5380686 56.473045,37.6991771 C57.6554969,38.119099 58.3490736,38.2709527 59.3189775,38.3222731 C60.2813112,38.3731924 60.9842658,38.2894182 61.9212184,38.0121548 C62.8417918,37.7397368 63.3719502,37.4609001 64.9598541,36.4139836 C65.7600359,35.8864195 66.440284,35.4547767 66.471519,35.4547767 C66.5027521,35.4547767 67.0803515,35.7862101 67.755071,36.1912952 C68.4297905,36.5963804 69.2405424,37.0360615 69.5567444,37.1683642 C71.712969,38.070559 74.0420268,38.0116978 76.1745223,37.0011185 C76.5559411,36.8203644 77.4539892,36.2817928 78.1701846,35.8042919 C78.886382,35.326791 79.4824177,34.9471029 79.4947099,34.9605418 C79.5070021,34.9739806 79.778356,35.5237025 80.0977177,36.1821464 C80.4170814,36.8405902 80.8182924,37.5832421 80.9893002,37.8324824 C81.7295619,38.9114073 82.5820041,39.7126563 83.7328255,40.4112529 C84.9174788,41.1303877 85.9246571,42.0861588 86,43.0151716 C86.0747409,43.9367439 85.6724545,44.7143746 84.5,46 C83.5849084,47.0034223 83.0750911,47.4348905 83.0774619,47.9920183 C83.0798385,48.5497457 83.4088314,49.1929459 83.987132,49.7704615 C84.9232353,50.7052914 85.8709987,50.75612 88.03125,50.40625 C88.5132136,50.3281926 93.0593045,49.4693031 94.03125,50.90625 C94.7020636,51.8979995 99.03125,59.2860751 99.03125,59.40625 C99.03125,59.4687341 99,59.90625 98.53125,59.90625 C98.0625,59.90625 93.1446104,58.2287002 92.5,58 C91.6528896,57.6994567 80.9355643,53.2313765 77.53125,51.90625 C68.4409578,48.3678658 64.9939487,46.8521107 63.53125,46.40625 C61.9505675,45.9244263 61.9052084,46.1821734 61.03125,46.40625 C59.9442448,46.6849498 45.6828955,52.0956892 37.03125,55.541804 C32.7589403,57.2435491 27.3306662,59.6891801 26.53125,59.90625 C26.1074799,60.0213206 25.7366528,59.8754941 25.4187688,59.4687707 Z" fill="url(#linearGradient-1)"></path></g></g></svg></div>
                <div class="technology-logo logo-scala"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"><defs><rect id="path-1" x="0" y="0" width="56" height="85"></rect><linearGradient x1="0%" y1="0%" x2="100%" y2="0%" id="linearGradient-4"><stop stop-color="#434343" offset="0%"></stop><stop stop-color="#4B4B4B" offset="100%"></stop></linearGradient><linearGradient x1="0%" y1="0%" x2="100%" y2="0%" id="linearGradient-5"><stop stop-color="#A00000" offset="0%"></stop><stop stop-color="#FF0000" offset="100%"></stop></linearGradient><linearGradient x1="0%" y1="0%" x2="100%" y2="0%" id="linearGradient-6"><stop stop-color="#39737B" offset="0%"></stop><stop stop-color="#5CD2DF" offset="100%"></stop></linearGradient></defs><g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd"><g transform="translate(15.000000, -1.000000)"><mask id="mask-2" fill="white"><use xlink:href="#path-1"></use></mask><g mask="url(#mask-2)"><g transform="translate(6.363636, -1.268657)"><g><path d="M0,11.840796 C0,11.840796 44.5454545,7.40049751 44.5454545,0 L44.5454545,17.761194 C44.5454545,17.761194 44.5454545,25.1616915 0,29.60199 L0,47.3631841 L0,11.840796 Z"  fill="url(#linearGradient-4)" fill-rule="nonzero" transform="translate(22.272727, 23.681592) scale(-1, 1) rotate(-180.000000) translate(-22.272727, -23.681592) "></path><path d="M0,35.5223881 C0,35.5223881 44.5454545,31.0820896 44.5454545,23.681592 L44.5454545,41.4427861 C44.5454545,41.4427861 44.5454545,48.8432836 0,53.2835821 L0,71.0447761 L0,35.5223881 Z" fill="url(#linearGradient-4)" fill-rule="nonzero" transform="translate(22.272727, 47.363184) scale(-1, 1) rotate(-180.000000) translate(-22.272727, -47.363184) "></path></g><g transform="translate(0.000000, 5.074627)"><path d="M0,12.0522388 C0,12.0522388 44.5454545,7.53264925 44.5454545,0 L44.5454545,18.0783582 C44.5454545,18.0783582 44.5454545,25.6110075 0,30.130597 L0,48.2089552 L0,12.0522388 Z" fill="url(#linearGradient-6)" fill-rule="nonzero"></path><path d="M0,36.1567164 C0,36.1567164 44.5454545,31.6371269 44.5454545,24.1044776 L44.5454545,42.1828358 C44.5454545,42.1828358 44.5454545,49.7154851 0,54.2350746 L0,72.3134328 L0,36.1567164 Z" fill="url(#linearGradient-6)" fill-rule="nonzero"></path><path d="M0,60.261194 C0,60.261194 44.5454545,55.7416045 44.5454545,48.2089552 L44.5454545,66.2873134 C44.5454545,66.2873134 44.5454545,73.8199627 0,78.3395522 L0,96.4179104 L0,60.261194 Z" fill="url(#linearGradient-6)" fill-rule="nonzero"></path></g></g></g></g></g></svg></div>
                <div class="technology-logo logo-javascript"><svg width="85px" height="85px" viewBox="0 0 85 85" version="1.1" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient x1="100%" y1="0%" x2="0%" y2="100%" id="linearGradient-7"><stop stop-color="#3ECFE0" offset="0%"></stop><stop stop-color="#4CA5AF" offset="100%"></stop></linearGradient></defs><g stroke="none" stroke-width="1" fill="none" fill-rule="nonzero"><g transform="translate(5.000000, 6.000000)"><rect fill="url(#linearGradient-7)" x="0" y="0" width="74" height="74"></rect><path d="M49.6936995,56.9169364 C51.1899405,59.3175189 53.1365866,61.0820396 56.5794737,61.0820396 C59.4717347,61.0820396 61.3193389,59.6616178 61.3193389,57.6989795 C61.3193389,55.3470575 59.4210347,54.5140369 56.2375431,53.1457512 L54.4925182,52.4100515 C49.4555271,50.3014318 46.1093238,47.6598643 46.1093238,42.0754978 C46.1093238,36.9313926 50.0981208,33.0153846 56.3318688,33.0153846 C60.7698918,33.0153846 63.9604578,34.5331274 66.2596461,38.5070645 L60.8241291,41.9364679 C59.6273721,39.8278482 58.3362894,38.9971448 56.3318688,38.9971448 C54.2873598,38.9971448 52.9915609,40.2715852 52.9915609,41.9364679 C52.9915609,43.99411 54.2885389,44.8271307 57.2833791,46.1015711 L59.028404,46.8361123 C64.9591309,49.3351742 68.3076923,51.8828966 68.3076923,57.6109272 C68.3076923,63.7861706 63.3709223,67.1692308 56.7410064,67.1692308 C50.2584744,67.1692308 46.0704145,64.1337453 44.0211892,60.1551738 L49.6936995,56.9169364 Z M25.035789,57.5112891 C26.132325,59.4229498 27.129819,61.0391721 29.5280493,61.0391721 C31.8213422,61.0391721 33.2680623,60.157491 33.2680623,56.7292461 L33.2680623,33.4069854 L40.2481622,33.4069854 L40.2481622,56.8219327 C40.2481622,63.9240419 36.0105813,67.1564864 29.8251751,67.1564864 C24.2363789,67.1564864 20.9998292,64.3144841 19.3538462,60.8914528 L25.035789,57.5112891 Z" fill="#FFFFFF"></path></g></g></svg></div>
            </div>

            <p>Read about <a href="https://gradle.org/features/">Gradle features</a> to learn what is possible with Gradle.</p>

            <h2>New projects with Gradle</h2>

            <p>Getting started with Gradle is easy! First, follow our guide to <a href="https://gradle.org/install/">download and install Gradle</a>, then check out Gradle <a href="https://gradle.org/guides/#getting-started">getting started guides</a> to create your first build.</p>

            <p>If you're currently using Maven, see a visual <a href="https://gradle.org/maven-vs-gradle/">Gradle vs Maven comparison</a> and follow the guide for <a href="https://guides.gradle.org/migrating-from-maven/">migrating from Maven to Gradle</a>.</p>

            <h2>Using existing Gradle builds</h2>

            <p>Gradle supports many major IDEs, including Android Studio, Eclipse, IntelliJ IDEA, Visual Studio 2017, and XCode.</p>

            <p>You can also invoke Gradle via its <a href="https://docs.gradle.org/current/userguide/command_line_interface.html">command line interface</a>
                in your terminal or through your continuous integration server.</p>

            <p><a href="https://scans.gradle.com/" title="Get started with build scans">Gradle build scans</a> help you understand build results, improve build performance, and collaborate to fix problems faster.</p>

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
