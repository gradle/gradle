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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:param name="version"/>

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="bookinfo/releaseinfo">
        <releaseinfo><xsl:value-of select="$version"/></releaseinfo>
    </xsl:template>

    <xsl:template match="sample">
        <example>
            <title><xsl:value-of select="@title"/></title>
            <programlisting><xsl:element name="include" namespace="http://www.w3.org/2001/XInclude">
                <xsl:attribute name="href">../../../src/samples/<xsl:value-of select="@src"/></xsl:attribute>
                <xsl:attribute name="parse">text</xsl:attribute>
            </xsl:element></programlisting>
        </example>
    </xsl:template>

    <xsl:template match="sampleOutput">
        <example>
            <title>Sample output - <xsl:value-of select="@src"/></title>
            <screen><xsl:element name="include" namespace="http://www.w3.org/2001/XInclude">
                <xsl:attribute name="href">../../../src/samples/userguideOutput/<xsl:value-of select="@src"/></xsl:attribute>
                <xsl:attribute name="parse">text</xsl:attribute>
            </xsl:element></screen>
        </example>
    </xsl:template>

</xsl:stylesheet>