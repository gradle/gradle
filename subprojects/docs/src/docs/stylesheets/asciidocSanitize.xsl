<?xml version="1.0"?>
<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:docbook="http://docbook.org/ns/docbook"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    exclude-result-prefixes="docbook xlink"
    version="1.0">
    <xsl:output method="xml" />

    <xsl:template match="/docbook:book">
        <xsl:apply-templates select="docbook:chapter"/>
    </xsl:template>

    <!-- Use <ulink url="..."/> instead of <link href="..."/> -->

    <xsl:template match="docbook:link[@xlink:href]">
        <ulink>
            <xsl:attribute name="url">
                <xsl:value-of select="@xlink:href"/>
            </xsl:attribute>
            <xsl:apply-templates select="node()"/>
        </ulink>
    </xsl:template>

    <!-- Rewrite Docbook tables as HTML tables -->

    <xsl:template match="//docbook:table//docbook:row">
        <tr>
            <xsl:apply-templates select="node()"/>
        </tr>
    </xsl:template>

    <xsl:template match="//docbook:table//docbook:thead//docbook:entry">
        <th>
            <xsl:apply-templates select="node()"/>
        </th>
    </xsl:template>

    <xsl:template match="//docbook:table//docbook:entry">
        <td>
            <xsl:apply-templates select="node()"/>
        </td>
    </xsl:template>

    <xsl:template match="//docbook:table/docbook:tgroup">
        <xsl:apply-templates select="node()"/>
    </xsl:template>

    <xsl:template match="//docbook:colspec"/>

    <!-- Remove namespaces from output -->

    <xsl:template match="*">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="node()|@*"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="@*">
        <xsl:attribute name="{local-name()}">
            <xsl:value-of select="."/>
        </xsl:attribute>
    </xsl:template>
</xsl:stylesheet>
