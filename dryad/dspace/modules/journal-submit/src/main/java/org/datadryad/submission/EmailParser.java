package org.datadryad.submission;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.jdom.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The interface of submission e-mail parsing classes
 * 
 * @author Akio Sone
 * @author Kevin S. Clarke
 */
public abstract class EmailParser {

	private static Logger LOGGER = LoggerFactory.getLogger(EmailParser.class);

	public abstract ParsingResult parseMessage(List<String> aMessage);

	protected String getStrippedText(String aInputString) {
		SAXBuilder builder = new SAXBuilder();
		// We have to replace characters used for XML syntax with entity refs
		String text = aInputString.replace("&", "&amp;").replace("<", "&lt;")
				.replace(">", "&gt;").replace("\"", "&quot;")
				.replace("'", "&apos;");

		// Check that we have well-formed XML
		try {
			Reader reader = new StringReader("<s>" + text + "</s>");
			builder.build(reader).getRootElement().getValue();
			
			// If we do, though, use our text string which has the entity refs;
			// they don't come out of our getValue() call above...
			return text.replaceAll("\\s+", " ");
		}
		catch (Exception details) {
			if (LOGGER.isWarnEnabled()) {
				LOGGER.warn("The following couldn't be parsed: \n" + text
						+ "\n" + details.getMessage());
			}

			return aInputString; // just return what we're given if problems
		}
	}

	public static String flipName(String aName) {
		if (aName == null || aName.trim().equals("") || aName.contains(",")) {
			return aName;
		}

		// a very simplistic first pass at this... you'd think there'd be a
		// OSS name parser out there already, but I'm not finding one...
		String[] parts = aName.split("\\s+");
		StringBuilder builder = new StringBuilder(parts[parts.length - 1]);

		builder.append(", ");

		for (int index = 0; index < parts.length - 1; index++) {
			builder.append(parts[index]).append(' ');
		}

		return builder.toString();
	}
}
