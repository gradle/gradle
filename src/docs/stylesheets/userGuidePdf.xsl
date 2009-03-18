<!--
  ~ Copyright 2009 the original author or authors.
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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:fo="http://www.w3.org/1999/XSL/Format"
        version="1.0">
    <xsl:import href="fo/docbook.xsl"/>
    <xsl:import href="userGuideCommon.xsl"/>

    <xsl:param name="fop1.extensions">1</xsl:param>
    <xsl:param name="paper.type">A4</xsl:param>
    <xsl:param name="body.start.indent">0pt</xsl:param>

    <xsl:param name="body.font.family">'Lucida Grande','Lucida Sans Unicode','Geneva','Verdana',sans-serif</xsl:param>
    <xsl:param name="body.font.master">11</xsl:param>

    <xsl:param name="ulink.show">0</xsl:param>

    <xsl:param name="textColour">#555555</xsl:param>
    <xsl:param name="titleColour">#6a915e</xsl:param>
    <xsl:param name="tableBorderColour">#d0d0d0</xsl:param>
    <xsl:param name="tableHeaderBackgroundColor">#f2f2f2</xsl:param>
    <xsl:param name="verbatimBackgroundColour">#f5f5f5</xsl:param>
    <xsl:param name="verbatimBorderColour">#d0d0d0</xsl:param>

    <xsl:attribute-set name="root.properties">
        <xsl:attribute name="color"><xsl:value-of select="$textColour"/></xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="component.title.properties">
        <xsl:attribute name="color"><xsl:value-of select="$titleColour"/></xsl:attribute>
        <xsl:attribute name="font-size">22pt</xsl:attribute>
        <xsl:attribute name="space-after">2cm</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="section.title.properties">
        <xsl:attribute name="color"><xsl:value-of select="$titleColour"/></xsl:attribute>
    </xsl:attribute-set>
    <xsl:attribute-set name="section.title.level1.properties">
        <xsl:attribute name="space-before">1.4em</xsl:attribute>
        <xsl:attribute name="space-after">0.6em</xsl:attribute>
        <xsl:attribute name="font-size">16pt</xsl:attribute>
    </xsl:attribute-set>
    <xsl:attribute-set name="section.title.level2.properties">
        <xsl:attribute name="font-size">12pt</xsl:attribute>
    </xsl:attribute-set>
    <xsl:attribute-set name="section.title.level3.properties">
        <xsl:attribute name="font-size">11pt</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="formal.title.properties">
        <xsl:attribute name="font-size">11pt</xsl:attribute>
    </xsl:attribute-set>
    <xsl:attribute-set name="table.properties">
        <xsl:attribute name="border">thin solid <xsl:value-of select="$tableBorderColour"/></xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="normal.para.spacing">
        <xsl:attribute name="line-height">140%</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="verbatim.properties">
        <xsl:attribute name="font-size">9pt</xsl:attribute>
        <xsl:attribute name="background-color"><xsl:value-of select="$verbatimBackgroundColour"/></xsl:attribute>
        <xsl:attribute name="start-indent">1.7em</xsl:attribute>
        <xsl:attribute name="end-indent">1.2em</xsl:attribute>
        <xsl:attribute name="padding-start">1.2em</xsl:attribute>
        <xsl:attribute name="padding-end">1.2em</xsl:attribute>
        <xsl:attribute name="space-before">1em</xsl:attribute>
        <xsl:attribute name="space-after">1em</xsl:attribute>
        <xsl:attribute name="padding-before">1em</xsl:attribute>
        <xsl:attribute name="padding-after">1em</xsl:attribute>
        <xsl:attribute name="border-start-width">0.5em</xsl:attribute>
        <xsl:attribute name="border-start-style">solid</xsl:attribute>
        <xsl:attribute name="border-start-color"><xsl:value-of select="$verbatimBorderColour"/></xsl:attribute>
    </xsl:attribute-set>

    <!-- Use custom titlepage -->
    <xsl:template name="book.titlepage.recto">
        <fo:block padding-before='6cm'>
            <fo:block font-size='30pt' font-weight='bold' font-stretch='expanded'>
                <xsl:attribute name="color"><xsl:value-of select="$titleColour"/></xsl:attribute>
                <xsl:value-of select="/book/bookinfo/title"/>
            </fo:block>
            <fo:block font-size='20pt' font-weight='bold' font-stretch='expanded' space-before='0.8em'>
                <xsl:attribute name="color"><xsl:value-of select="$titleColour"/></xsl:attribute>
                <xsl:value-of select="/book/bookinfo/subtitle"/>
            </fo:block>
            <fo:block font-size='14pt' font-weight='bold' font-stretch='expanded' space-before='6em'>
                Version <xsl:value-of select="/book/bookinfo/releaseinfo"/>
            </fo:block>
        </fo:block>
    </xsl:template>

    <xsl:template name="table.row.properties">
        <xsl:if test="ancestor::thead">
            <xsl:attribute name="background-color"><xsl:value-of select="$tableHeaderBackgroundColor"/></xsl:attribute>
            <xsl:attribute name="keep-with-next.within-column">always</xsl:attribute>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>