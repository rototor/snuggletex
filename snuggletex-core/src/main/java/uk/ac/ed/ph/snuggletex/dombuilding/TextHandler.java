package uk.ac.ed.ph.snuggletex.dombuilding;

import org.w3c.dom.Element;
import uk.ac.ed.ph.snuggletex.internal.DOMBuilder;
import uk.ac.ed.ph.snuggletex.internal.SnuggleParseException;
import uk.ac.ed.ph.snuggletex.tokens.CommandToken;

public class TextHandler implements CommandHandler {
    @Override
    public void handleCommand(DOMBuilder builder, Element parentElement, CommandToken token) throws SnuggleParseException {
        String extractedValue = (String) token.getArguments()[0].getSlice().extract();
        builder.appendMathMLTextElement(parentElement, "mtext", extractedValue, false);
    }
}
