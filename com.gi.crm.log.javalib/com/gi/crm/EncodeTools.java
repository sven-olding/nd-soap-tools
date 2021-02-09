package com.gi.crm;

import org.owasp.encoder.Encode;

/**
 * Collection of encoding function used for XSS protection.
 * 
 * @author btietze
 * @since 8.8.1
 */
public class EncodeTools
{
	/**
	 * Contains the HTML tags which are allowed for forHtmlContentWithHtml
	 */
	private static final String	allowedHtmlTags[];

	// fill the static array
	static {
		allowedHtmlTags = new String[5];
		allowedHtmlTags[0] = "b";
		allowedHtmlTags[1] = "i";
		allowedHtmlTags[2] = "u";
		allowedHtmlTags[3] = "em";
		allowedHtmlTags[4] = "strong";
	}

	/**
	 * Used for Html content encoding
	 * 
	 * @param strToEncode string which should be encoded. Any existing line break will be replaced with the html tag <br>
	 * @return the encoded string
	 * @since 8.8.1
	 */
	public static String forHtmlContent(String strToEncode)
	{
		if (strToEncode != null) {
			return Encode.forHtmlContent(strToEncode).replace("\n", "<br>").replace("\r", "");
		}
		return null;
	}

	/**
	 * Used for Html encoding if the content will be used in Html attributes and Html content. This function is slightly
	 * less efficient as forHtmlContent and forHtmlAttribute and should only be used if really necessary.
	 * 
	 * @param strToEncode string which should be encoded.
	 * @return the encoded string
	 * @since 8.8.1
	 */
	public static String forHtml(String strToEncode)
	{
		if (strToEncode != null) {
			return Encode.forHtml(strToEncode);
		}
		return null;
	}

	/**
	 * Used for Html content encoding with allowing of the following html tags: <br>
	 * , <b>, <i>, <u>, <strong> and <em>
	 * 
	 * @param strToEncode string which should be encoded.
	 * @return the encoded string
	 * @since 8.8.1
	 */
	public static String forHtmlContentWithHtml(String strToEncode)
	{
		if (strToEncode != null) {
			String result = EncodeTools.forHtmlContent(strToEncode).replaceAll("\\&lt;br[ ]*\\\\?\\/?\\&gt;", "<br>");
			for (String allowedTag : allowedHtmlTags) {

				result = result.replaceAll("\\&lt;" + allowedTag + "\\&gt;", "<" + allowedTag + ">").replaceAll(
						"\\&lt;\\/" + allowedTag + "\\&gt;", "</" + allowedTag + ">");
			}

			result = result.replaceAll("\\&amp;nbsp;", "&nbsp;");

			return result;
		}
		return null;
	}

	/**
	 * Used for Html attribute encoding
	 * 
	 * @param strToEncode string which should be encoded
	 * @return the encoded string
	 * @since 8.8.1
	 */
	public static String forHtmlAttribute(String strToEncode)
	{
		if (strToEncode != null) {
			return Encode.forHtmlAttribute(strToEncode);
		}
		return null;
	}

	/**
	 * Used for JavaScript block encoding
	 * 
	 * @param strToEncode string which should be encoded
	 * @return the encoded string
	 * @since 8.8.1
	 */
	public static String forJavaScriptBlock(String strToEncode)
	{
		if (strToEncode != null) {
			return Encode.forJavaScriptBlock(strToEncode);
		}
		return null;
	}

	/**
	 * Used for JavaScript attribute encoding
	 * 
	 * @param strToEncode string which should be encoded
	 * @return the encoded string
	 * @since 8.8.1
	 */
	public static String forJavaScriptAttribute(String strToEncode)
	{
		if (strToEncode != null) {
			return Encode.forJavaScriptAttribute(strToEncode);
		}
		return null;
	}

	/**
	 * Used for Url component encoding
	 * 
	 * @param strToEncode string which should be encoded
	 * @return the encoded string
	 * @since 8.8.1
	 */
	public static String forUriComponent(String strToEncode)
	{
		if (strToEncode != null) {
			return Encode.forUriComponent(strToEncode);
		}
		return null;
	}

	/**
	 * Remove possibly dangerous script code from url
	 * 
	 * @param strToSanitize
	 * @return sanitized url
	 * @since 8.8.1
	 */
	public static String sanitizeUrl(String strToSanitize)
	{
		if (strToSanitize != null) {
			return strToSanitize.replaceAll("(?i)javascript\\s*:", "");
		}
		return null;
	}
}
