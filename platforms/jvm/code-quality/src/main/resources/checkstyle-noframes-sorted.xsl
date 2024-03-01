<?xml version="1.0"?>
<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html" indent="yes"/>
    <xsl:param name="gradleVersion"/>

    <xsl:decimal-format decimal-separator="." grouping-separator="," />

    <xsl:template match="checkstyle">
        <html>
            <head>
                <title>Checkstyle Violations</title>
                <!-- vaguely adapted from Gradle's CSS -->
                <style type="text/css">
                    body {
                        background-color: #fff;
                        color: #02303A;
                    }

                    a {
                        color: #1DA2BD;
                    }
                    a.link {
                        color: #02303A;
                    }

                    p {
                        font-size: 1rem;
                    }

                    h1 a[name] {
                        margin: 0;
                        padding: 0;
                    }

                    tr:nth-child(even) {
                        background: white;
                    }

                    th {
                        font-weight:bold;
                    }
                    tr {
                        background: #efefef;
                    }
                    table th, td, tr {
                        font-size:100%;
                        border: none;
                        text-align: left;
                        vertical-align: top;
                    }
                </style>
            </head>
            <body>
                <p>
                    <a name="top"><h1>Checkstyle Results</h1></a>
                </p>
                <hr align="left" width="95%" size="1"/>
                <h2>Summary</h2>
                <table class="summary" width="95%" >
                    <tr>
                        <th>Total files checked</th>
                        <th>Total violations</th>
                        <th>Files with violations</th>
                    </tr>
                    <tr>
                        <td>
                            <xsl:number level="any" value="count(descendant::file)"/>
                        </td>
                        <td>
                            <xsl:number level="any" value="count(descendant::error)"/>
                        </td>
                        <td>
                            <xsl:number level="any" value="count(descendant::file[error])"/>
                        </td>
                    </tr>
                </table>
                <hr align="left" width="95%" size="1"/>
                <div class="violations">
                    <h2>Violations</h2>
                    <p>
                        <xsl:choose>
                            <xsl:when test="count(descendant::error) > 0">
                                <table class="filelist" width="95%">
                                    <tr>
                                        <th>File</th>
                                        <th>Total violations</th>
                                    </tr>
                                    <xsl:for-each select="file[error]">
                                        <!-- sort by number of errors and then alphabetically -->
                                        <xsl:sort data-type="number" order="descending" select="count(descendant::error)"/>
                                        <xsl:sort select="@name"/>
                                        <xsl:variable name="errors" select="count(descendant::error)"/>
                                        <tr>
                                            <td><a href="#{generate-id(@name)}"><xsl:value-of select="@name"/></a></td>
                                            <td><xsl:value-of select="$errors"/></td>
                                        </tr>
                                    </xsl:for-each>
                                </table>
                                <p/>
                                <xsl:apply-templates>
                                    <!-- sort entries by file name alphabetically -->
                                    <xsl:sort select="@name"/>
                                </xsl:apply-templates>
                                <p/>
                            </xsl:when>
                            <xsl:otherwise>
                                No violations were found.
                            </xsl:otherwise>
                        </xsl:choose>
                    </p>
                </div>
                <hr align="left" width="95%" size="1"/>
                <p>Generated by <a href="https://gradle.org"><xsl:value-of select="$gradleVersion"/></a> with <a href="https://checkstyle.sourceforge.io/">Checkstyle <xsl:value-of select="@version"/></a>.</p>
            </body>
        </html>
    </xsl:template>

    <!-- A single file with violations -->
    <xsl:template match="file[error]">
        <div class="file-violation">
            <h3>
                <a class="link" name="{generate-id(@name)}"><xsl:value-of select="@name"/></a>
            </h3>
            <table class="violationlist" width="95%">
                <tr>
                    <th>Severity</th>
                    <th>Description</th>
                    <th>Line Number</th>
                </tr>
                <xsl:apply-templates select="error"/>
            </table>
            <p/>
            <a href="#top">Back to top</a>
            <p/>
        </div>
    </xsl:template>

    <!-- A single row in the list of violations -->
    <xsl:template match="error">
        <tr>
            <td>
                <xsl:value-of select="@severity"/>
            </td>
            <td>
                <xsl:value-of select="@message"/>
            </td>
            <td>
                <xsl:value-of select="@line"/>
            </td>
        </tr>
    </xsl:template>

</xsl:stylesheet>
