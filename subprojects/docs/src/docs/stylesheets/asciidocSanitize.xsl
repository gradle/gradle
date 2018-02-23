<?xml version="1.0"?>
<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:docbook="http://docbook.org/ns/docbook"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    exclude-result-prefixes="docbook xlink"
    version="1.0">
    <xsl:output method="xml" />

    <xsl:template match="/docbook:book">
        <xsl:apply-templates select="docbook:chapter | docbook:appendix"/>
    </xsl:template>

    <!-- Use <ulink url="..."/> instead of <link href="..."/> -->

    <xsl:template match="docbook:link[@xlink:href]">
        <ulink>
            <xsl:attribute name="url">
                <xsl:value-of select="@xlink:href"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </ulink>
    </xsl:template>

    <!-- Rewrite Docbook tables as HTML tables -->

    <xsl:template match="//docbook:table//docbook:row">
        <tr>
            <xsl:apply-templates/>
        </tr>
    </xsl:template>

    <xsl:template match="//docbook:table//docbook:thead//docbook:entry">
        <th>
            <xsl:apply-templates/>
        </th>
    </xsl:template>

    <xsl:template match="//docbook:table//docbook:entry">
        <td>
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <xsl:template match="//docbook:table/docbook:tgroup">
        <xsl:apply-templates/>
    </xsl:template>

<!--
    <xsl:template match="//docbook:entry/docbook:simpara[last() = 1]">
        <xsl:apply-templates/>
    </xsl:template>
-->

    <xsl:template match="//docbook:colspec"/>

    <xsl:template match="//docbook:simpara">
        <para>
            <xsl:apply-templates/>
        </para>
    </xsl:template>

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