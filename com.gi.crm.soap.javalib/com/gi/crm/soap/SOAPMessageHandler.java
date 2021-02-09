package com.gi.crm.soap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import com.gi.crm.log.SimpleLog;
import com.gi.crm.log.XMLFormatter;

/**
 * Handles processing of SOAP XML Messages, logging, user agent etc. will be set here
 * 
 * @author SOL
 * @version 20171108
 */
public class SOAPMessageHandler implements SOAPHandler<SOAPMessageContext>, Serializable
{
	/**
	 * 
	 */
	private static final long	serialVersionUID	= 21346579463746L;

	/** User-agent */
	private static final String	USER_AGENT			= "GEDYS IntraWare CRM";

	private SimpleLog			logger				= null;

	/** Default constructor */
	public SOAPMessageHandler()
	{
	}

	/**
	 * Parameter constructor
	 * 
	 * @param logger - log infos go here instead of standard output stream
	 */
	public SOAPMessageHandler(SimpleLog logger)
	{
		this.logger = logger;
	}

	public Set<QName> getHeaders()
	{
		return null;
	}

	public void close(MessageContext arg0)
	{
	}

	public boolean handleFault(SOAPMessageContext context)
	{
		SOAPMessage msg = context.getMessage();
		boolean isOutboundMessage = (Boolean) context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);

		if (!isOutboundMessage) {
			try {
				if (msg.getSOAPBody().getFault() != null) {
					if (logger != null) {
						logger.logError("Fault detail: " + msg.getSOAPBody().getFault().getDetail().getTextContent());
					} else {
						System.out
								.println("Fault detail: " + msg.getSOAPBody().getFault().getDetail().getTextContent());
					}
				} else {
					if (logger != null) {
						logger.logError("No fault detail available");
					} else {
						System.out.println("No fault detail available");
					}
				}
			} catch (SOAPException e) {
			}
		}

		ByteArrayOutputStream b = null;
		try {
			if (logger == null) {
				msg.writeTo(System.out);
			} else {
				b = new ByteArrayOutputStream();
				msg.writeTo(b);
				logger.log("" + b.toString("utf-8"));
			}
		} catch (SOAPException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				b.close();
			} catch (IOException e) {
			}
		}

		return true;
	}

	@SuppressWarnings("unchecked")
	public boolean handleMessage(SOAPMessageContext context)
	{
		SOAPMessage msg = context.getMessage();

		boolean isOutboundMessage = (Boolean) context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
		try {
			if (isOutboundMessage) { // request
				// add xml declaration & encoding
				msg.setProperty(SOAPMessage.WRITE_XML_DECLARATION, "true");
				msg.setProperty(SOAPMessage.CHARACTER_SET_ENCODING, "utf-8");
				
				// set custom user agent header
				Map<String, List<String>> headers = (Map<String, List<String>>) context
						.get(MessageContext.HTTP_REQUEST_HEADERS);
				if (headers == null) {
					headers = new HashMap<String, List<String>>();
				}

				headers.put("User-Agent", Collections.singletonList(USER_AGENT));
				context.put(MessageContext.HTTP_REQUEST_HEADERS, headers);
			}

		} catch (SOAPException e1) {
			e1.printStackTrace();
		}

		// message logging
		ByteArrayOutputStream baos = null;
		try {
			baos = new ByteArrayOutputStream();
			msg.writeTo(baos);
			if (logger == null) {
				System.out.println((isOutboundMessage ? "Request:\n" : "Response:\n")
						+ XMLFormatter.format(baos.toString("utf-8")));
			} else {
				logger.log((isOutboundMessage ? "Request:\n" : "Response:\n")
						+ XMLFormatter.format(baos.toString("UTF-8")));
			}
		} catch (SOAPException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (baos != null)
					baos.close();
			} catch (IOException e) {
			}
		}
		context.setMessage(msg);

		return true;
	}
}
