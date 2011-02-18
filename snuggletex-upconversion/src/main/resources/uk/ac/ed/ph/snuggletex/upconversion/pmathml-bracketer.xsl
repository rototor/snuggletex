<!--

$Id$

Adds brackets to the enhanced Presentation MathML to make certain
things a bit clearer.

This is still experimental.

Copyright (c) 2008-2011, The University of Edinburgh
All Rights Reserved

-->
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:s="http://www.ph.ed.ac.uk/snuggletex"
  xmlns:local="http://www.ph.ed.ac.uk/snuggletex/pmathml-enhancer"
  xmlns:m="http://www.w3.org/1998/Math/MathML"
  xmlns="http://www.w3.org/1998/Math/MathML"
  exclude-result-prefixes="xs m s local"
  xpath-default-namespace="http://www.w3.org/1998/Math/MathML">

  <xsl:import href="pmathml-utilities.xsl"/>
  <xsl:import href="snuggletex-utilities.xsl"/>
  <xsl:import href="upconversion-options.xsl"/>
  <xsl:strip-space elements="m:*"/>

  <xsl:variable name="s:function-colours" select="('blue','green','red')" as="xs:string+"/>

  <xsl:function name="s:get-function-colour" as="xs:string">
    <xsl:param name="level" as="xs:integer"/>
    <xsl:sequence select="$s:function-colours[min(($level, count($s:function-colours)))]"/>
  </xsl:function>

  <xsl:variable name="s:grey" select="'#cccccc'" as="xs:string"/>

  <!-- ************************************************************ -->

  <!-- Entry point -->
  <xsl:template name="s:bracket-pmathml">
    <xsl:param name="elements" as="element()*"/>
    <xsl:apply-templates select="$elements">
      <xsl:with-param name="level" select="1" as="xs:integer"/>
    </xsl:apply-templates>
  </xsl:template>

  <!-- ************************************************************ -->

  <!-- Function applications -->
  <xsl:template match="*[following-sibling::*[1][self::mo[.='&#x2061;']]]">
    <xsl:param name="level" select="1" as="xs:integer"/>
    <!--<mstyle color="{s:get-function-colour($level)}">-->
      <xsl:copy-of select="."/>
      <!-- We won't copy the apply function operator, as it uses up too much whitespace -->
      <mfenced open="(" close=")">
        <xsl:apply-templates select="following-sibling::*[2]" mode="function-argument">
          <xsl:with-param name="level" select="$level + 1"/>
        </xsl:apply-templates>
      </mfenced>
    <!--</mstyle>-->
  </xsl:template>

  <xsl:template match="mfenced" mode="function-argument">
    <xsl:param name="level" select="1" as="xs:integer"/>
    <xsl:apply-templates select="*">
      <xsl:with-param name="level" select="$level"/>
    </xsl:apply-templates>
  </xsl:template>

  <xsl:template match="*" mode="function-argument">
    <xsl:param name="level" select="1" as="xs:integer"/>
    <xsl:call-template name="copy">
      <xsl:with-param name="level" select="$level"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="mo[.='&#x2061;'] | *[preceding-sibling::*[1][self::mo[.='&#x2061;']]]">
    <!-- Handled in above template -->
  </xsl:template>

  <!-- Implicit multiplication made explicit -->
  <xsl:template match="mo[.='&#x2062;']">
    <mo color="{$s:grey}">&#x22c5;</mo>
  </xsl:template>

  <xsl:template match="*" name="copy">
    <xsl:param name="level" select="1" as="xs:integer"/>
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:apply-templates>
        <xsl:with-param name="level" select="$level"/>
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>

