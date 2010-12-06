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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:import href="html/chunkfast.xsl"/>
    <xsl:import href="userGuideHtmlCommon.xsl"/>

    <xsl:param name="root.filename">index</xsl:param>
    <xsl:param name="chunk.section.depth">0</xsl:param>
    <xsl:param name="chunk.quietly">1</xsl:param>
    <xsl:param name="use.id.as.filename">1</xsl:param>
    <xsl:param name="section.autolabel">0</xsl:param>
    <xsl:param name="chapter.autolabel">0</xsl:param>
    <xsl:param name="appendix.autolabel">0</xsl:param>
    <xsl:param name="table.autolabel">0</xsl:param>

    <!-- No table of contents -->
    <xsl:param name="generate.toc"/>

    <!-- customise the stylesheets to add to the <head> element -->
    <xsl:template name="output.html.stylesheets">
        <link href="base.css" rel="stylesheet" type="text/css"/>
        <link href="style.css" rel="stylesheet" type="text/css"/>
        <link href="dsl.css" rel="stylesheet" type="text/css"/>
    </xsl:template>

    <!-- customise the layout of the html page -->
    <xsl:template name="chunk-element-content">
        <xsl:param name="prev"/>
        <xsl:param name="next"/>
        <xsl:param name="nav.context"/>
        <xsl:param name="content">
            <xsl:apply-imports/>
        </xsl:param>

        <html>
            <xsl:call-template name="html.head">
                <xsl:with-param name="prev" select="$prev"/>
                <xsl:with-param name="next" select="$next"/>
            </xsl:call-template>

            <body>
                <xsl:call-template name="body.attributes"/>
                <div class="sidebar">
                    <ul>
                        <xsl:apply-templates select="." mode="sidebar"/>
                        <xsl:apply-templates select="/book/section/table[@role = 'dslTypes']" mode="sidebar"/>
                    </ul>
                </div>
                <div class="content">
                    <xsl:copy-of select="$content"/>
                </div>
                <script src="sidebar.js" type="text/javascript"/>
            </body>
        </html>
        <xsl:value-of select="$chunk.append"/>
    </xsl:template>

    <!--
      - Navigation sidebar
      -->

    <xsl:template match="book" mode="sidebar">
        <li class='sidebarHeading selected'>
            Home
        </li>
        <ul class='sections'>
            <xsl:apply-templates select="section" mode="sidebar"/>
        </ul>
    </xsl:template>

    <xsl:template match="chapter" mode="sidebar">
        <li>
            <xsl:call-template name="customXref">
                <xsl:with-param name="target" select="/"/>
                <xsl:with-param name="content">
                    <xsl:text>Home</xsl:text>
                </xsl:with-param>
            </xsl:call-template>
        </li>
        <li class='sidebarHeading selected'>
            <xsl:value-of select="title"/>
        </li>
        <ul class='sections'>
            <xsl:apply-templates select="section" mode="sidebar"/>
        </ul>
    </xsl:template>

    <xsl:template match="section" mode="sidebar">
        <li>
            <xsl:call-template name="customXref">
                <xsl:with-param name="target" select="."/>
            </xsl:call-template>
        </li>
    </xsl:template>

    <xsl:template match="table" mode="sidebar">
        <li class='sidebarHeading'>
            <xsl:value-of select="title"/>
        </li>
        <xsl:apply-templates select="tr/td[1]" mode="sidebar"/>
    </xsl:template>

    <xsl:template match="td" mode="sidebar">
        <li>
            <xsl:apply-templates select="link"/>
        </li>
    </xsl:template>
</xsl:stylesheet>