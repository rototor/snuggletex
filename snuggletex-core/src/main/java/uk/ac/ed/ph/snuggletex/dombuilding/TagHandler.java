package uk.ac.ed.ph.snuggletex.dombuilding;

import org.w3c.dom.Element;
import uk.ac.ed.ph.snuggletex.internal.DOMBuilder;
import uk.ac.ed.ph.snuggletex.internal.SnuggleParseException;
import uk.ac.ed.ph.snuggletex.tokens.ArgumentContainerToken;
import uk.ac.ed.ph.snuggletex.tokens.CommandToken;

public class TagHandler implements CommandHandler {
    @Override
    public void handleCommand(DOMBuilder builder, Element parentElement, CommandToken token) throws SnuggleParseException {
        ArgumentContainerToken optionalArgument = token.getOptionalArgument();
        String extractedValue = (String) token.getArguments()[0].getSlice().extract();
        String width = "1.5cm";

        if(optionalArgument != null){
            width = token.getOptionalArgument().getSlice().extract()+"cm";
        }

        Element mspace = builder.appendMathMLElement(parentElement, "mspace");
        mspace.setAttribute("width", width);
        builder.appendMathMLTextElement(parentElement, "mtext", "("+extractedValue+")", false);
    }
}
