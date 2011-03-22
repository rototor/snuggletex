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
  xmlns:local="http://www.ph.ed.ac.uk/snuggletex/bracket-pmathml"
  xmlns:m="http://www.w3.org/1998/Math/MathML"
  xmlns="http://www.w3.org/1998/Math/MathML"
  exclude-result-prefixes="xs m s local"
  xpath-default-namespace="http://www.w3.org/1998/Math/MathML">

  <xsl:import href="pmathml-utilities.xsl"/>
  <xsl:import href="snuggletex-utilities.xsl"/>
  <xsl:import href="upconversion-options.xsl"/>
  <xsl:strip-space elements="m:*"/>

  <!-- ************************************************************ -->

  <xsl:variable name="local:grey" select="'#cccccc'" as="xs:string"/>

  <xsl:variable name="local:bracket-colours"
    select="('#660000', '#006600', '#000099', '#666600', 'black')"
    as="xs:string+"/>

  <xsl:function name="local:get-bracket-colour" as="xs:string">
    <xsl:param name="level" as="xs:integer"/>
    <xsl:sequence select="$local:bracket-colours[min(($level, count($local:bracket-colours)))]"/>
  </xsl:function>

  <!-- ************************************************************ -->

  <!-- Entry point -->
  <xsl:template name="s:bracket-pmathml" as="element()*">
    <xsl:param name="elements" as="element()*"/>
    <xsl:message>
      <xsl:copy-of select="$elements"/>
    </xsl:message>
    <xsl:call-template name="local:process-group">
      <xsl:with-param name="elements" select="$elements"/>
      <xsl:with-param name="implicit-level" select="1" as="xs:integer" tunnel="yes"/>
      <xsl:with-param name="fence-level" select="1" as="xs:integer" tunnel="yes"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ************************************************************ -->

  <xsl:template name="local:process-group" as="element()*">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:param name="elements" as="element()*" required="yes"/>
    <xsl:choose>
      <xsl:when test="false() and count($elements)=1 and $elements[1][self::mrow]">
        <!-- Unwrap <mrow/> containers so we enter everything in the same way -->
        <xsl:call-template name="local:process-group">
          <xsl:with-param name="elements" select="$elements[1]/*"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="count($elements)=3 and $elements[2][self::mo[.=('/', '&#xf7;')]]">
        <!-- Convert divisions to fractions for readability -->
        <mfrac>
          <xsl:call-template name="s:maybe-wrap-in-mrow">
            <xsl:with-param name="elements" as="element()*">
              <xsl:call-template name="local:process-group">
                <xsl:with-param name="elements" select="$elements[1]"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
          <xsl:call-template name="s:maybe-wrap-in-mrow">
            <xsl:with-param name="elements" as="element()*">
              <xsl:call-template name="local:process-group">
                <xsl:with-param name="elements" select="$elements[3]"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
        </mfrac>
      </xsl:when>
      <xsl:when test="count($elements)=3 and $elements[2][self::mo[.='&#x2061;']]">
        <!-- Function application -->
        <!-- Copy function -->
        <xsl:copy-of select="$elements[1]"/>
        <!-- We strip out the &ApplyFunction; as it uses too much space -->
        <!-- Now fence up the argument -->
        <xsl:variable name="arg" as="element()" select="$elements[3]"/>
        <xsl:call-template name="local:make-fence">
          <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
          <xsl:with-param name="next" select="if ($arg[self::mfenced] and count($arg/*)=1) then $arg/* else $arg"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="$elements" mode="local:bracket-pmathml"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- ************************************************************ -->

  <!-- Explicit fence gets decorated -->
  <xsl:template match="mfenced[count(*)=1]" mode="local:bracket-pmathml">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:call-template name="local:make-fence">
      <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
      <xsl:with-param name="next" select="if (count(*)=1 and mrow) then *[1]/* else *"/>
    </xsl:call-template>
  </xsl:template>

  <!-- Implicit multiplication will be displayed with \cdot but no brackets -->
  <xsl:template match="mrow[mo[.='&#x2062;']]" mode="local:bracket-pmathml" priority="2">
    <xsl:param name="implicit-level" as="xs:integer" required="yes" tunnel="yes"/>
    <mrow>
      <xsl:call-template name="local:process-group">
        <xsl:with-param name="elements" select="*"/>
        <xsl:with-param name="implicit-level" select="$implicit-level + 1" tunnel="yes"/>
      </xsl:call-template>
    </mrow>
  </xsl:template>

  <!-- Unary minus container will be fenced for clarity -->
  <xsl:template match="mrow[*[1][self::mo and .='-']]" mode="local:bracket-pmathml" priority="3">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:param name="implicit-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:call-template name="local:make-fence">
      <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
      <xsl:with-param name="next" select="*"/>
    </xsl:call-template>
  </xsl:template>

  <!-- Don't fence standard addition, subtraction or function application -->
  <xsl:template match="mrow[mo[.= ('+', '-', '&#x2061;')]]" mode="local:bracket-pmathml" priority="2">
    <xsl:param name="implicit-level" as="xs:integer" required="yes" tunnel="yes"/>
    <mrow>
      <xsl:call-template name="local:process-group">
        <xsl:with-param name="elements" select="*"/>
        <xsl:with-param name="implicit-level" select="$implicit-level + 1" tunnel="yes"/>
      </xsl:call-template>
    </mrow>
  </xsl:template>

  <!-- Turn all other operators (not at the top level) into fences -->
  <xsl:template match="mrow[mo]" mode="local:bracket-pmathml">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:call-template name="local:make-fence">
      <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
      <xsl:with-param name="next" select="*"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ************************************************************ -->

  <!-- Implicit multiplication displayed as \cdot -->
  <xsl:template match="mo[.='&#x2062;']" mode="local:bracket-pmathml" priority="2">
    <mspace width="-0.15em"/>
    <mo color="{$local:grey}">&#x22c5;</mo>
    <mspace width="-0.15em"/>
  </xsl:template>

  <!-- Other unary operators get a bit of space round them depending on the current level -->
  <xsl:template match="mo[preceding-sibling::* and following-sibling::*]" mode="local:bracket-pmathml">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:param name="implicit-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:variable name="level" as="xs:integer" select="$fence-level + $implicit-level - 1"/>
    <mspace width="{0.3 div $level}em"/>
    <xsl:copy-of select="."/>
    <mspace width="{0.3 div $level}em"/>
  </xsl:template>

  <!-- ************************************************************ -->

  <!-- Handle top and bottom of fractions as groups in normal way -->
  <xsl:template match="mfrac" mode="local:bracket-pmathml">
    <mfrac>
      <xsl:for-each select="*">
        <xsl:call-template name="s:maybe-wrap-in-mrow">
          <xsl:with-param name="elements" as="element()*">
            <xsl:call-template name="local:process-group">
              <xsl:with-param name="elements" select="."/>
            </xsl:call-template>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:for-each>
    </mfrac>
  </xsl:template>

  <!-- Sups/subs similar to fractions, but we'll wrap complex args in extra brackets -->
  <xsl:template match="msub|msup|msubsup" mode="local:bracket-pmathml">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:for-each select="*">
        <xsl:call-template name="s:maybe-wrap-in-mrow">
          <xsl:with-param name="elements" as="element()*">
            <xsl:choose>
              <xsl:when test="self::mrow">
                <!-- Complex argument, so we'll bracket it up -->
                <xsl:call-template name="local:make-fence">
                  <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
                  <xsl:with-param name="next" select="."/>
                </xsl:call-template>
              </xsl:when>
              <xsl:otherwise>
                <!-- Simple argument, so just keep it -->
                <xsl:call-template name="local:process-group">
                  <xsl:with-param name="elements" select="."/>
                  <xsl:with-param name="fence-level" select="$fence-level" tunnel="yes"/>
                </xsl:call-template>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:for-each>
    </xsl:copy>
  </xsl:template>

  <!-- All other elements are copied as-is -->
  <xsl:template match="*" mode="local:bracket-pmathml">
    <xsl:copy-of select="."/>
  </xsl:template>

  <!-- ************************************************************ -->

  <xsl:template name="local:make-fence" as="element(mstyle)">
    <xsl:param name="fence-level" as="xs:integer" required="yes" tunnel="yes"/>
    <xsl:param name="next" as="element()*" required="yes"/>
    <mstyle color="{local:get-bracket-colour($fence-level)}">
      <mfenced open="(" close=")">
        <mstyle color="black">
          <xsl:call-template name="s:maybe-wrap-in-mrow">
            <xsl:with-param name="elements" as="element()*">
              <xsl:call-template name="local:process-group">
                <xsl:with-param name="fence-level" select="$fence-level + 1" tunnel="yes"/>
                <xsl:with-param name="elements" select="$next"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
        </mstyle>
      </mfenced>
    </mstyle>
  </xsl:template>

</xsl:stylesheet>

