package com.gi.crm.tools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Utility class for parsing/evaluating XPath expressions
 *
 * @author SOL
 */
public class XPathParser
{
	private final Document	xmlDocument;
	private final XPath		xPath;

	/**
	 * Constructor
	 *
	 * @param xml XML String
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 * @throws UnsupportedEncodingException
	 */
	public XPathParser(String xml)
			throws ParserConfigurationException, UnsupportedEncodingException, SAXException, IOException
	{
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		builderFactory.setNamespaceAware(true);
		DocumentBuilder builder = builderFactory.newDocumentBuilder();
		xmlDocument = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		xPath = XPathFactory.newInstance().newXPath();
		xPath.setNamespaceContext(new NamespaceResolver(xmlDocument));
	}

	/**
	 * Constructor
	 *
	 * @param xml XML String
	 * @param defaultNSPrefix default namespace prefix
	 * @param defaultNSURI default namespace uri
	 * @throws IOException
	 * @throws SAXException
	 * @throws UnsupportedEncodingException
	 * @throws ParserConfigurationException
	 */
	public XPathParser(String xml, String defaultNSPrefix, String defaultNSURI)
			throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException
	{
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		builderFactory.setNamespaceAware(true);
		DocumentBuilder builder = builderFactory.newDocumentBuilder();
		xmlDocument = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		xPath = XPathFactory.newInstance().newXPath();
		xPath.setNamespaceContext(new NamespaceResolver(xmlDocument, defaultNSPrefix, defaultNSURI));
	}

	/**
	 * Evaluate an expression and return a vector of strings with the results<br />
	 *
	 * @param expression the XPath expression
	 * @return the results as string vector
	 * @throws XPathExpressionException if there is an error with the expression
	 */
	public Vector<String> evaluate(String expression) throws XPathExpressionException
	{
		Vector<String> ret = new Vector<>();

		NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			ret.add(node.getNodeValue());
		}

		return ret;
	}

	/**
	 * Evaluates an XPath expression and returns the result as xml string
	 *
	 * @param expression the XPath expression
	 * @return Xml String with the results
	 * @throws XPathExpressionException if there is an error with the expression
	 * @throws ParserConfigurationException if there is an error building the xml doc
	 * @throws TransformerException if there is an error transforming the xml document to string
	 */
	public String evaluateToXmlString(String expression)
			throws XPathExpressionException, ParserConfigurationException, TransformerException
	{
		Document newXmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = newXmlDocument.createElement("root");
		newXmlDocument.appendChild(root);

		NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			Node copyNode = newXmlDocument.importNode(node, true);
			root.appendChild(copyNode);
		}
		DOMSource domSource = new DOMSource(newXmlDocument);
		StringWriter writer = new StringWriter();
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.transform(domSource, result);
		return writer.toString();
	}

	/**
	 * Evaluates an expression and returns a NodeList of matching nodes
	 * 
	 * @param expression the XPath expression
	 * @return list of matches
	 * @throws XPathExpressionException if something is wrong with your expression
	 */
	public NodeList evaluateToNodeList(String expression) throws XPathExpressionException
	{
		return (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
	}
}
