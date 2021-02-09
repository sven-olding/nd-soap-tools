package com.gi.crm.log;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Node;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

/**
 * Helper class to pretty-print XML
 * 
 * @see https://stackoverflow.com/questions/139076/how-to-pretty-print-xml-from-java
 * @author SOL
 * @version 20171108
 */
public class XMLFormatter
{

	/**
	 * Pretty print xml string
	 * 
	 * @param xml
	 * @return pretty xml
	 */
	public static String format(String xml)
	{

		try {
			final InputSource src = new InputSource(new StringReader(xml));
			final Node document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(src)
					.getDocumentElement();
			final Boolean keepDeclaration = Boolean.valueOf(xml.startsWith("<?xml"));

			final DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
			final DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
			final LSSerializer writer = impl.createLSSerializer();

			writer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
			writer.getDomConfig().setParameter("xml-declaration", keepDeclaration);

			return writer.writeToString(document);
		} catch (Exception e) {
			return xml;
		}
	}
}
