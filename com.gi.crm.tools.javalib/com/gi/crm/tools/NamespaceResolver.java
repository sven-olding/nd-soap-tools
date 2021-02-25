package com.gi.crm.tools;

import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Document;

public class NamespaceResolver implements NamespaceContext
{
	// Store the source document to search the namespaces
	private Document	sourceDocument;

	private String		defaultPrefix;

	private String		defaultPrefixURI;

	public NamespaceResolver(Document document)
	{
		sourceDocument = document;
	}

	public NamespaceResolver(Document document, String defaultPrefix, String defaultPrefixURI)
	{
		sourceDocument = document;
		this.defaultPrefix = defaultPrefix;
		this.defaultPrefixURI = defaultPrefixURI;
	}

	// The lookup for the namespace uris is delegated to the stored document.
	public String getNamespaceURI(String prefix)
	{
		if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
			return sourceDocument.lookupNamespaceURI(null);		
		} else {
			if(defaultPrefix!=null && !defaultPrefix.isEmpty() && prefix.equals(defaultPrefix)) {
				return defaultPrefixURI;
			}
			return sourceDocument.lookupNamespaceURI(prefix);
		}
	}

	public String getPrefix(String namespaceURI)
	{
		return sourceDocument.lookupPrefix(namespaceURI);
	}

	@SuppressWarnings("rawtypes")
	public Iterator getPrefixes(String namespaceURI)
	{
		return null;
	}
}