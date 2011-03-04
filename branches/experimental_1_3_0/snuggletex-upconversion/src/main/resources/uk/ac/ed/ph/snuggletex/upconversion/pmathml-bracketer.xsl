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

  <xsl:variable name="s:grey" select="'#cccccc'" as="xs:string"/>

  <xsl:variable name="s:colours" select="('#660000', '#006600', '#000099', '#666600', 'black')"
    as="xs:string+"/>

  <xsl:function name="s:get-colour" as="xs:string">
    <xsl:param name="level" as="xs:integer"/>
    <xsl:sequence select="$s:colours[min(($level, count($s:colours)))]"/>
  </xsl:function>

  <!-- ************************************************************ -->

  <!-- Entry point -->
  <xsl:template name="s:bracket-pmathml">
    <xsl:param name="elements" as="element()*"/>
    <xsl:param name="without-outer-mrow" select="if (count($elements)=1 and $elements[1][self::mrow]) then $elements[1]/* else $elements" as="element()*"/>
    <xsl:apply-templates select="$elements" mode="bracket-pmathml">
      <xsl:with-param name="elements" select="$without-outer-mrow"/>
      <xsl:with-param name="implicit-level" select="1" as="xs:integer" tunnel="yes"/>
      <xsl:with-param name="fence-level" select="1" as="xs:integer" tunnel="yes"/>
    </xsl:apply-templates>
  </xsl:template>

  <!-- ************************************************************ -->

  <!-- Function application -->
  <xsl:template match="mrow[count(*)=3 and *[2][self::mo[.='&#x2061;']]]" mode="bracket-pmathml">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:param name="arg" as="element()" select="*[3]"/>
    <!-- Copy function -->
    <mrow>
      <xsl:copy-of select="*[1]"/>
      <!-- We strip out the &ApplyFunction; as it uses too much space -->
      <xsl:call-template name="local:make-fence">
        <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
        <xsl:with-param name="next" select="if ($arg[self::mfenced] and count($arg/*)=1) then $arg/* else $arg"/>
      </xsl:call-template>
    </mrow>
  </xsl:template>

  <!-- Implicit multiplication made explicit -->
  <xsl:template match="mo[.='&#x2062;']" mode="bracket-pmathml">
    <mspace width="-0.15em"/>
    <mo color="{$s:grey}">&#x22c5;</mo>
    <mspace width="-0.15em"/>
  </xsl:template>

  <!-- Other operators get a bit of space -->
  <xsl:template match="mo" mode="bracket-pmathml">
    <xsl:param name="implicit-level" as="xs:integer" required="yes" tunnel="yes"/>
    <mspace width="{0.3 div $implicit-level}em"/>
    <xsl:copy-of select="."/>
    <mspace width="{0.3 div $implicit-level}em"/>
  </xsl:template>

  <!-- Explicit fence -->
  <xsl:template match="mfenced[count(*)=1]" mode="bracket-pmathml">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:call-template name="local:make-fence">
      <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
      <xsl:with-param name="next" as="element()" select="*"/>
    </xsl:call-template>
  </xsl:template>

  <!-- Implicit grouping -->
  <xsl:template match="mrow" mode="bracket-pmathml">
    <xsl:param name="implicit-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:copy>
      <xsl:apply-templates mode="bracket-pmathml">
        <xsl:with-param name="implicit-level" select="$implicit-level + 1" tunnel="yes"/>
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>

  <!-- Default -->
  <xsl:template match="*" mode="bracket-pmathml">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:apply-templates mode="bracket-pmathml"/>
    </xsl:copy>
  </xsl:template>

  <!-- ************************************************************ -->

  <xsl:template name="local:make-fence">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:param name="next" as="element()" required="yes"/>
    <mstyle color="{s:get-colour($fence-level)}">
      <mfenced open="(" close=")">
        <mstyle color="black">
          <xsl:apply-templates select="$next" mode="bracket-pmathml">
            <xsl:with-param name="fence-level" select="$fence-level + 1" tunnel="yes"/>
          </xsl:apply-templates>
        </mstyle>
      </mfenced>
    </mstyle>
  </xsl:template>

</xsl:stylesheet>

