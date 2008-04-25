/* $Id: BeanToStringOptions.java,v 1.1 2008/01/14 10:54:06 dmckain Exp $
 *
 * Copyright (c) 2003 - 2008 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.aardvark.commons.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Trivial annotation that can be applied to a <tt>getX()</tt> method to prevent its
 * details from being listed by {@link ObjectUtilities#beanToString(Object)}.
 *
 * @author  David McKain
 * @version $Revision: 1.1 $
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeanToStringOptions {
    
    PropertyOptions value() default PropertyOptions.SHOW_FULL;

}