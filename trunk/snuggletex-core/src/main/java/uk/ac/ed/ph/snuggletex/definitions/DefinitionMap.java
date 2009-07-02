/* $Id:DefinitionMap.java 179 2008-08-01 13:41:24Z davemckain $
 *
 * Copyright 2009 University of Edinburgh.
 * All Rights Reserved
 */
package uk.ac.ed.ph.snuggletex.definitions;

import uk.ac.ed.ph.snuggletex.dombuilding.CommandHandler;
import uk.ac.ed.ph.snuggletex.dombuilding.EnvironmentHandler;
import uk.ac.ed.ph.snuggletex.dombuilding.InterpretableSimpleMathHandler;
import uk.ac.ed.ph.snuggletex.semantics.Interpretation;
import uk.ac.ed.ph.snuggletex.semantics.InterpretationType;
import uk.ac.ed.ph.snuggletex.semantics.MathInterpretation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates a collection of {@link BuiltinCommand}s and {@link BuiltinEnvironment}s,
 * with convenience methods for creating commands and environments of various types.
 * 
 * @see BuiltinCommand
 * @see BuiltinEnvironment
 *
 * @author  David McKain
 * @version $Revision:179 $
 */
public final class DefinitionMap {
    
    /** Map of built-in commands, keyed on name */
    public final Map<String, BuiltinCommand> builtinCommandMap;
    
    /** Map of built-in environments, keyed on name */
    public final Map<String, BuiltinEnvironment> builtinEnvironmentMap;
    
    /** Share instance of {@link InterpretableSimpleMathHandler} since it is stateless */
    private final InterpretableSimpleMathHandler interpretableSimpleMathBuilder;
    
    public DefinitionMap() {
        this.interpretableSimpleMathBuilder = new InterpretableSimpleMathHandler();
        this.builtinCommandMap = new HashMap<String, BuiltinCommand>();
        this.builtinEnvironmentMap = new HashMap<String, BuiltinEnvironment>();
    }
    
    public BuiltinCommand getCommandByTeXName(String texName) {
        return builtinCommandMap.get(texName);
    }
    
    public BuiltinEnvironment getEnvironmentByTeXName(String texName) {
        return builtinEnvironmentMap.get(texName);
    }
    
    //-------------------------------------------------------
    
    /**
     * Tests whether the name of a command or environment is "inputable". Ones which cannot be
     * directly input are enclosed in angle brackets. These commands are created during token
     * fixing.
     * 
     * @param texName
     */
    public static boolean isInputableTeXName(final String texName) {
        return texName!=null && !(texName.charAt(0)=='<' && texName.length()>3 && texName.endsWith(">"));
    }
    
    public static EnumMap<InterpretationType, Interpretation> makeInterpretationMap(final Interpretation interpretation) {
        if (interpretation==null) {
            return null;
        }
        EnumMap<InterpretationType, Interpretation> result = new EnumMap<InterpretationType, Interpretation>(InterpretationType.class);
        result.put(interpretation.getType(), interpretation);
        return result;
    }
    
    public static EnumMap<InterpretationType, Interpretation> makeInterpretationMap(final Interpretation... interpretations) {
        if (interpretations.length==0) {
            return null;
        }
        EnumMap<InterpretationType, Interpretation> result = new EnumMap<InterpretationType, Interpretation>(InterpretationType.class);
        for (Interpretation interpretation : interpretations) {
            result.put(interpretation.getType(), interpretation);
        }
        return result;
    }
    
    //-------------------------------------------------------
    
    public BuiltinCommand addSimpleCommand(final String name, final EnumSet<LaTeXMode> allowedModes,
            final CommandHandler handler, final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.SIMPLE, false, 0,
                allowedModes, null, null,
                handler, context, null));
    }
    
    public BuiltinCommand addSimpleCommand(final String name, final EnumSet<LaTeXMode> allowedModes,
            final Interpretation[] interpretations, final CommandHandler handler,
            final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.SIMPLE, false, 0,
                allowedModes, null, makeInterpretationMap(interpretations),
                handler, context, null));
    }
    
    public BuiltinCommand addSimpleCommand(final String name, final EnumSet<LaTeXMode> allowedModes,
            final Interpretation interpretation, final CommandHandler handler,
            final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.SIMPLE, false, 0,
                allowedModes, null, makeInterpretationMap(interpretation),
                handler, context, null));
    }

    /** Convenience methods for creating simple MATH-only commands. */
    public BuiltinCommand addSimpleMathCommand(final String name, final MathInterpretation... interpretations) {
        return addSimpleMathCommand(name, interpretations, interpretableSimpleMathBuilder);
    }
    
    public BuiltinCommand addSimpleMathCommand(final String name,
            final MathInterpretation interpretation, final CommandHandler handler) {
        return addCommand(new BuiltinCommand(name, CommandType.SIMPLE, false, 0,
                Globals.MATH_MODE_ONLY, null, makeInterpretationMap(interpretation),
                handler, null, null));
    }
    
    public BuiltinCommand addSimpleMathCommand(final String name,
            final MathInterpretation[] interpretations, final CommandHandler handler) {
        return addCommand(new BuiltinCommand(name, CommandType.SIMPLE, false, 0,
                Globals.MATH_MODE_ONLY, null, makeInterpretationMap(interpretations),
                handler, null, null));
    }

    
    public BuiltinCommand addCombinerCommand(final String name, final EnumSet<LaTeXMode> allowedModes,
            final CombinerTargetMatcher combinerTargetMatcher,
            final CommandHandler handler, final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.COMBINER, false, 0,
                allowedModes, null,
                null, handler,
                context, combinerTargetMatcher));
    }
    

    public BuiltinCommand addComplexCommand(final String name, final boolean allowOptionalArgument,
            final int arguments, final EnumSet<LaTeXMode> allowedModes,
            final LaTeXMode[] argumentModes,
            final CommandHandler handler, final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.COMPLEX, allowOptionalArgument, arguments,
                allowedModes, argumentModes,
                null, handler,
                context, null));
    }
    
    public BuiltinCommand addComplexCommandSameArgMode(final String name, final boolean allowOptionalArgument,
            final int arguments, final EnumSet<LaTeXMode> allowedModes, final CommandHandler handler,
            final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.COMPLEX, allowOptionalArgument, arguments,
                allowedModes, null,
                null, handler,
                context, null));
    }
    
    public BuiltinCommand addComplexCommandSameArgMode(final String name, final boolean allowOptionalArgument,
            final int arguments, final EnumSet<LaTeXMode> allowedModes,
            final Interpretation interpretation, final CommandHandler handler,
            final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.COMPLEX, allowOptionalArgument, arguments,
                allowedModes, null,
                makeInterpretationMap(interpretation), handler,
                context, null));
    }
    
    public BuiltinCommand addComplexCommandOneArg(final String name, final boolean allowOptionalArgument,
            final EnumSet<LaTeXMode> allowedModes, final LaTeXMode argumentMode,
            final CommandHandler handler, final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.COMPLEX, allowOptionalArgument, 1,
                allowedModes, new LaTeXMode[] { argumentMode },
                null, handler,
                context, null));
    }
    
    public BuiltinCommand addComplexCommandOneArg(final String name, final boolean allowOptionalArgument,
            final EnumSet<LaTeXMode> allowedModes, final LaTeXMode argumentMode,
            final Interpretation interpretation, final CommandHandler handler,
            final TextFlowContext context) {
        return addCommand(new BuiltinCommand(name, CommandType.COMPLEX, allowOptionalArgument, 1,
                allowedModes, new LaTeXMode[] { argumentMode },
                makeInterpretationMap(interpretation), handler,
                context, null));
    }
    
    private BuiltinCommand addCommand(final BuiltinCommand command) {
        if (isInputableTeXName(command.getTeXName())) {
            builtinCommandMap.put(command.getTeXName(), command);
        }
        return command;
    }
    

    
    public BuiltinEnvironment addEnvironment(final String name, final EnumSet<LaTeXMode> allowedModes,
            final LaTeXMode contentMode, final Interpretation interpretation,
            final EnvironmentHandler handler, final TextFlowContext context) {
        return addEnvironment(new BuiltinEnvironment(name, false, 0, allowedModes,
                contentMode, makeInterpretationMap(interpretation), handler, context));
    }
    
    public BuiltinEnvironment addEnvironment(final String name, final boolean allowOptionalArgument,
            final int argumentCount, final EnumSet<LaTeXMode> allowedModes,
            final LaTeXMode contentMode, final Interpretation interpretation,
            final EnvironmentHandler handler, final TextFlowContext context) {
        return addEnvironment(new BuiltinEnvironment(name, allowOptionalArgument, argumentCount,
                allowedModes, contentMode, makeInterpretationMap(interpretation), handler, context));
    }
    
    private BuiltinEnvironment addEnvironment(final BuiltinEnvironment environment) {
        if (isInputableTeXName(environment.getTeXName())) {
            builtinEnvironmentMap.put(environment.getTeXName(), environment);
        }
        return environment;
    }
}
