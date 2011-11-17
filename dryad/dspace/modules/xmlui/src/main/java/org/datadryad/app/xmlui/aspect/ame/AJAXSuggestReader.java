package org.datadryad.app.xmlui.aspect.ame;

import java.io.ByteArrayInputStream;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Response;
import org.apache.cocoon.reading.AbstractReader;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.dspace.app.xmlui.utils.ContextUtil;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.xml.sax.SAXException;

public class AJAXSuggestReader extends AbstractReader
{

	protected Response response;
	
	public void generate() throws IOException, SAXException,
			ProcessingException
	{
	
		
		this.response = ObjectModelHelper.getResponse(objectModel);
		
		int itemID = -1;
		String field = "";
		String cv = "";
		String mp = "";
		String format = "";
		String text = "";
		String ignore = "";
		
		String host = ConfigurationManager.getProperty("ame.host");
		
		try
		{
			
			Context context = ContextUtil.obtainContext(objectModel);
			itemID = parameters.getParameterAsInteger("itemID");
			Item item = Item.find(context, itemID);
			text += getItemTitle(item);
			text += getItemAbstract(item);
			
			text += getItemKeywords(item);
			

			field = parameters.getParameter("field");
						
			cv = ConfigurationManager.getProperty("ame." + field + ".cv");
			mp = ConfigurationManager.getProperty("ame." + field + ".mp");
			format = ConfigurationManager.getProperty("ame." + field + ".format");
		
			ignore = getIgnoreList(field, item);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		text = URLEncoder.encode(text);
		ignore = URLEncoder.encode(ignore);
		
		String url = host + "/suggest?cv=" + cv + "&tx=" + text + "&mp=" + mp + "&fmt=" + format + "&ex=" + ignore;
		GetMethod get = new GetMethod(url);
		HttpClient httpClient = new HttpClient();
		httpClient.executeMethod(get);
		String result = get.getResponseBodyAsString();
		if (result != null)
		{
			ByteArrayInputStream in = new ByteArrayInputStream(result.getBytes("UTF-8"));
			byte[] buffer = new byte[8192];
			
			response.setHeader("Content-Length", String.valueOf(result.length()));
			response.setContentType("text/json");

			int length;
			while ((length = in.read(buffer)) > -1)
				out.write(buffer, 0, length);
			out.flush();
		}
	}
	/**
	 * Obtain the item's title.
	 */
	public static String getItemTitle(Item item) {
		DCValue[] titles = item.getDC("title", Item.ANY, Item.ANY);

		String title;
		if (titles != null && titles.length > 0) title = titles[0].value;
		else title = null;
		return title;
	}
	
	public static String getItemKeywords(Item item) {
		String text = " ";
		DCValue[] keywords = item.getMetadata("dc.subject");
		if (keywords != null)
		{
			for (DCValue kw: keywords)
				text += kw.value + ", ";
		}
		
		keywords = item.getMetadata("dwc.ScientificName");
		if (keywords != null) 
		{
			for (DCValue kw: keywords)
				text += kw.value;
		}
		return text;
	}
	/**
	 * Obtain the item's description.
	 */
	public static String getItemAbstract(Item item) {
		DCValue[] descriptions = item.getMetadata("dc.description.abstract");
		if (descriptions == null || descriptions.length == 0)
			descriptions = item.getMetadata("dc.description");
		

		String description;
		if (descriptions != null && descriptions.length > 0) description = descriptions[0].value;
		else description = null;
		return description;
	}
	
	public static String getIgnoreList(String field, Item item) {
		String ignore = "";
		
		DCValue[] keywords = item.getMetadata(field.replace("_", "."));
		if (keywords != null)
		{
			for (DCValue kw: keywords)
				ignore += kw.value + "|";
		}
		
		return ignore;
	}
}
