package cloud.fogbow.ras.core.plugins.interoperability.opennebula;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import cloud.fogbow.ras.core.constants.Messages;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class OpenNebulaUnmarshallerContents {

	private final static Logger LOGGER = Logger.getLogger(OpenNebulaUnmarshallerContents.class);
	
	private Document document;
	
	public OpenNebulaUnmarshallerContents(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			InputSource source = new InputSource(new StringReader(xml));
			this.document = builder.parse(source);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			LOGGER.error(Messages.Error.ERROR_WHILE_CREATING_RESPONSE_BODY);
		}
	}

	public String getContentOfLastElement(String tag) {
		NodeList list = this.document.getElementsByTagName(tag);
		int lastIndex = list.getLength()-1;
		String lastElement = list.item(lastIndex).getTextContent();
		return lastElement;
	}

	public boolean containsExpressionContext(String expression) {
		NodeList nodes;
		try {
			XPath xPath = XPathFactory.newInstance().newXPath();
			nodes = (NodeList) xPath.compile(expression).evaluate(this.document, XPathConstants.NODESET);
			Node item = nodes.item(0);
			if (item != null) {
				return true;
			}
		} catch (XPathExpressionException e) {
			LOGGER.error(e); // TODO Fix error message...
		}
		return false;
	}
}
