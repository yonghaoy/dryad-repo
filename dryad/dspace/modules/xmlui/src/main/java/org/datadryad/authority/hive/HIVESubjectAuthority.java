package org.datadryad.authority.hive;

import java.io.InputStream;


import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.dspace.content.authority.Choice;
import org.dspace.content.authority.ChoiceAuthority;
import org.dspace.content.authority.Choices;
import org.dspace.core.ConfigurationManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class HIVESubjectAuthority implements ChoiceAuthority {



	public Choices getMatches(String field, String text, int collection, int start, int limit,
			String locale) 
	{
		Choices choices = new Choices(true);

		
		try
		{
			String cv = ConfigurationManager.getProperty("ame." + field + ".cv");
			
			String host = ConfigurationManager.getProperty("ame.host");
			
			text = URLEncoder.encode(text);
			String u = host + "/suggest?cv=" + cv + "&tx=" + text + "&mp=1&fmt=list";

			URL url = new URL(u);
			InputStream is = url.openStream();
			JSONTokener jt = new JSONTokener(is);
			
			List<Choice> cs = new ArrayList<Choice>();
			JSONArray array = new JSONArray(jt);
			for (int i = 0; i<array.length(); i++)
			{
				JSONObject o = array.getJSONObject(i);
				try
				{
					String id = o.getString("key");
					String label = o.getString("title");
					String value = o.getString("title");
					Choice c = new Choice(id, label, value);
					cs.add(c);
				} catch (Exception e) {
					
				}
			}
			
			int confidence;
			if (cs.size() == 0)
				confidence = Choices.CF_NOTFOUND;
			if (cs.size() == 1) 
				confidence = Choices.CF_UNCERTAIN;
			else
				confidence = Choices.CF_AMBIGUOUS;
			
			//boolean more = false;
			// if hits > (start + resultsSize)
			choices = new Choices(cs.toArray(new Choice[0]), start, cs.size(), confidence, false); 
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		return choices;
	}





	public Choices getBestMatch(String field, String text, int collection,
			String locale) {
		return getMatches(field, text, collection, 0, 2, locale);
	}


	public String getLabel(String field, String key, String locale) {
		return key;
	}

}
