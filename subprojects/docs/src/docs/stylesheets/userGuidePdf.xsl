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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:import href="xhtml/docbook.xsl"/>
    <xsl:import href="userGuideHtmlCommon.xsl"/>

    <xsl:output method="xml"
                encoding="UTF-8"
                indent="no"/>

    <!-- Use custom <head> content, to include stylesheets and bookmarks -->

    <xsl:template name="output.html.stylesheets">
        <link href="base.css" rel="stylesheet" type="text/css"/>
        <link href="docs.css" rel="stylesheet" type="text/css"/>
        <link href="userguide.css" rel="stylesheet" type="text/css"/>
        <link href="print.css" rel="stylesheet" type="text/css" media="print"/>
    </xsl:template>

    <xsl:template name="user.head.content">
        <bookmarks>
            <xsl:apply-templates select="chapter|appendix" mode="bookmarks"/>
        </bookmarks>
    </xsl:template>

    <xsl:template match="*" mode="bookmarks">
        <bookmark>
            <xsl:attribute name="name">
                <xsl:apply-templates select="." mode="object.title.markup"/>
            </xsl:attribute>
            <xsl:attribute name="href">#<xsl:call-template name="object.id"/></xsl:attribute>
            <xsl:apply-templates select="section[parent::chapter|parent::appendix]" mode="bookmarks"/>
        </bookmark>
    </xsl:template>

    <!-- Use custom chapter headings -->
    <xsl:template name="component.title">
        <h2>
            <xsl:call-template name="anchor">
                <xsl:with-param name="node" select=".."/>
                <xsl:with-param name="conditional" select="0"/>
            </xsl:call-template>
            <xsl:apply-templates select=".." mode="label.markup"/>
        </h2>
        <h1>
            <xsl:apply-templates select=".." mode="title.markup"/>
        </h1>
    </xsl:template>
</xsl:stylesheet>