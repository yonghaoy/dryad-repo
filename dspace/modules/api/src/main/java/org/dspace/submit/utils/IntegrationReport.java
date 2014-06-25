package org.dspace.submit.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.net.InetAddress;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Date;
import java.util.List;
import java.util.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;


import org.dspace.core.Email;
import org.dspace.app.xmlui.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.LogManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

import org.apache.log4j.Logger;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.workflow.DryadWorkflowUtils;
import org.dspace.workflow.WorkflowItem;
import org.dspace.core.I18nUtil;
public class IntegrationReport{
    
    private static Logger log = Logger.getLogger(IntegrationReport.class);
    public static final String FULLNAME = "fullname";
	public static final String METADATADIR = "metadataDir";
    public static final String INTEGRATED = "integrated";
    public static final String NOTIFY_WEEKLY = "notifyWeekly";
    public static final String ALLOW_REVIEW_WORKFLOW = "allowReviewWorkflow";
    public static final String PUBLICATION_BLACKOUT = "publicationBlackout";
    public static final String JOURNAL_ID = "journalID";
    public static final String NUMBER_ACCEPTED = "number_accepted";
    public static final String NUMBER_IN_REVIEW = "number_in_review";
    public static final String NUMBER_DEPOSITS_IN_REVIEW = "number_deposits_in_review";
    public static final String NUMBER_ARCHIVED = "number_archived";
    public static final String NUMBER_HIDDEN_TO_PUBLIC = "number_hidder_to_public";

    public static final String ACCEPTED = "<article_status>accepted</article_status>"; 
    public static final String SUBMITTED = "<article_status>submitted</article_status>"; 
    public static final String IN_REVIEW = "<article_status>in review</article_status>"; 
    public static final String UNDER_REVIEW = "<article_status>under review</article_status>"; 
    public static final String REVISION_IN_REVIEW = "<article_status>revision in review</article_status>"; 
 
    public static DateUtil date_util = new DateUtil();
    public static final java.util.Map<String, Map<String, String>> journalProperties = new HashMap<String, Map<String, String>>();
    public static final java.util.Map<String, Map<String, Map<String,String>>> article_in_review = new HashMap<String, Map<String, Map<String,String>>>();
    public static final java.util.Map<String, Map<String, Map<String,String>>> article_archived = new HashMap<String, Map<String, Map<String,String>>>();
	public static void main(String[] args) throws Exception{
        String journalPropFile = ConfigurationManager.getProperty("submit.journal.config");
        Properties properties = new Properties();
		Context myContext = new Context();
        try {
            properties.load(new InputStreamReader(new FileInputStream(journalPropFile), "UTF-8"));
            String journalTypes = properties.getProperty("journal.order");

            for (int i = 0; i < journalTypes.split(",").length; i++) {
                String journalType = journalTypes.split(",")[i].trim();
                String str = "journal." + journalType + ".";
				log.debug("reading config for journal " + journalType);
				log.debug("fullname " + properties.getProperty(str + FULLNAME));
                if(properties.getProperty(str+INTEGRATED)!=null&&properties.getProperty(str+INTEGRATED).toLowerCase().equals("true")){
			
						Map<String, String> map = new HashMap<String, String>();
						String journal_dir = properties.getProperty(str + METADATADIR);
						map.put(FULLNAME, properties.getProperty(str + FULLNAME));
						map.put(NOTIFY_WEEKLY, properties.getProperty(str + NOTIFY_WEEKLY));
						map.put(JOURNAL_ID, journalType);
						map.put(NUMBER_ACCEPTED,String.valueOf(count_accept(journal_dir)));
					    map.put(NUMBER_ARCHIVED,String.valueOf(count_archived(myContext,properties.getProperty(str + FULLNAME))));
						if(properties.getProperty(str+ALLOW_REVIEW_WORKFLOW)!=null){
								if(properties.getProperty(str+ALLOW_REVIEW_WORKFLOW).equals("true")){

										map.put(NUMBER_IN_REVIEW,String.valueOf(count_review(journal_dir)));
					                    map.put(NUMBER_DEPOSITS_IN_REVIEW,String.valueOf(count_deposit_in_review(myContext,properties.getProperty(str + FULLNAME))));


								}
						}
						if(properties.getProperty(str+PUBLICATION_BLACKOUT)!=null){
								if(properties.getProperty(str+PUBLICATION_BLACKOUT).equals("true")){

										map.put(NUMBER_HIDDEN_TO_PUBLIC,String.valueOf(count_blackout(myContext,properties.getProperty(str + FULLNAME))));


								}
						}
						String key = properties.getProperty(str + FULLNAME);
						if(key!=null&&key.length()>0){
								journalProperties.put(key, map);
						}
				    
				}

            }
			sendEmail(myContext);

        }catch (IOException e) {
				log.error("Error while loading journal properties", e);
		}

    }

	//count number of accepted publications
	public static int count_accept(String journal_dir){
			File journal_folder = new File(journal_dir);
			int count_accepted = 0;
			if(journal_folder.exists()&&journal_folder.isDirectory()&&journal_folder.list().length>0){
					File[] xmls = journal_folder.listFiles();
					for(File xml : xmls){
						    log.debug("reading xml file " + xml.getName());
							if(date_util.isThisWeek(xml.lastModified())){
									try{
											BufferedReader br = new BufferedReader(new FileReader(xml.getPath()));
											String line;
											while ((line = br.readLine()) != null) {
													// case-insensitive
													if(line.trim().toLowerCase().equals(ACCEPTED)){
															count_accepted ++;	
															break;
													}

											}
											br.close();
									}
									catch(FileNotFoundException e1){
											e1.printStackTrace();
									}
									catch(IOException e2){
											e2.printStackTrace();
									}
							}
					}
			}
		    return count_accepted; 	

	}

    //count number of in review publications
    public static int count_review(String journal_dir){
			File journal_folder = new File(journal_dir);
			int count_review = 0;
            DateUtil date_util = new DateUtil();
			File[] xmls = journal_folder.listFiles();
			if(journal_folder.exists()&&journal_folder.isDirectory()&&journal_folder.list().length>0){
					for(File xml : xmls){
						    log.debug("reading xml file " + xml.getName());
						
							if(date_util.isThisWeek(xml.lastModified())){
									try{
											BufferedReader br = new BufferedReader(new FileReader(xml.getPath()));
											String line;
											while ((line = br.readLine()) != null) {
													// case-insensitive
													String line_str = line.trim().toLowerCase();
													if(line_str.equals(SUBMITTED) || line_str.equals(IN_REVIEW) ||line_str.equals(UNDER_REVIEW) || line_str.equals(REVISION_IN_REVIEW)){
															count_review++;	
															break;
													}

											}
											br.close();
									}
									catch(FileNotFoundException e1){
											e1.printStackTrace();
									}
									catch(IOException e2){
											e2.printStackTrace();
									}
							}
					}
			}
		    return count_review; 	

    }
	public static void sendEmail(Context myContext) throws Exception{
			for(Map.Entry<String,Map<String,String>> journal : journalProperties.entrySet()){
					Map<String,String> journal_entry = journal.getValue();
	
				Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(myContext.getCurrentLocale(), "integration_report"));;
				
				//add recipient
				String recipents = journal_entry.get(NOTIFY_WEEKLY);
				if(recipents!=null){
				
						if(recipents.contains(",")){
								String[] recipent_arr = recipents.split(",");
								for(int i = 0; i < recipent_arr.length;i++){
										email.addRecipient(recipent_arr[i].trim());
								}
						}
						else{
								email.addRecipient(recipents.trim());
						}
				}
				
				//set subject
				String subject = "Dryad/" + journal_entry.get(FULLNAME) + " integration report";

				email.addArgument(new Date());

				//set content
				if(journal_entry.get(NUMBER_IN_REVIEW)!=null){
						email.addArgument("New notifications of manuscripts in review: " + journal_entry.get(NUMBER_IN_REVIEW));
				}
				else{
						email.addArgument("");		
				}
				if(journal_entry.get(NUMBER_DEPOSITS_IN_REVIEW)!=null){
						email.addArgument("New deposits of data in review: " + journal_entry.get(NUMBER_DEPOSITS_IN_REVIEW));
				}
				else{
						email.addArgument("");		
				}
				email.addArgument(journal_entry.get(NUMBER_ACCEPTED));
				email.addArgument(journal_entry.get(NUMBER_ARCHIVED));
				
				if(journal_entry.get(NUMBER_HIDDEN_TO_PUBLIC)!=null){
						email.addArgument("New deposits of data in review: " + journal_entry.get(NUMBER_HIDDEN_TO_PUBLIC));
				}
				else{
						email.addArgument("");		
				}
				//doi and manuscript number for articles in review:
				String article_info_review = "";
				if(journal_entry.get(NUMBER_DEPOSITS_IN_REVIEW)!=null){
						for(Map.Entry<String,Map<String,Map<String,String>>> articles_info_set : article_in_review.entrySet()){
								if(articles_info_set.getKey().equals(journal_entry.get(FULLNAME))){
										log.info("read article info from journal " + journal_entry.get(FULLNAME) + "\n");
										article_info_review += "<b>Review</b>\n";
										article_info_review += "<table>";
										for(Map.Entry<String,Map<String,String>> article_info : articles_info_set.getValue().entrySet()){

												Map<String,String> single_article_info = article_info.getValue();
												article_info_review +="<tr><td>";
												article_info_review += single_article_info.get("author") + "</td><td>";
												article_info_review += single_article_info.get("manu_number") + "</td><td>";
												article_info_review += single_article_info.get("doi") + "</td></tr>";

										}
										article_info_review +="</table>";

								}


						}
				}
				email.addArgument(article_info_review);
				String article_info_archived = "";

				for(Map.Entry<String,Map<String,Map<String,String>>> articles_info_set : article_archived.entrySet()){
						if(articles_info_set.getKey().equals(journal_entry.get(FULLNAME))){
								log.info("read article info from journal " + journal_entry.get(FULLNAME) + "\n");
								article_info_archived += "<b>Archived</b>\n";
								article_info_archived += "<table>";
								for(Map.Entry<String,Map<String,String>> article_info : articles_info_set.getValue().entrySet()){
										Map<String,String> single_article_info = article_info.getValue();
										article_info_archived +="<tr><td>";
										article_info_archived += single_article_info.get("author") + "</td><td>";
										article_info_archived += single_article_info.get("manu_number") + "</td><td>";
										article_info_archived += single_article_info.get("doi") + "</td></tr>";
										
								}
								article_info_archived +="</table>";
						
						}
				
				
				}

				email.addArgument(article_info_archived);
				email.addArgument(subject);

				try
				{
						email.send();
				}
				catch (MessagingException me)
				{
						log.error("\nError sending email:");
						log.error(" - Error: " ,  me);
						System.exit(1);
				}
				log.info("\nEmail sent successfully!\n" + subject);

		}


	}

	public static int count_archived(Context myContext, String journal_name) throws Exception{
			int count_archived = 0;
			TableRowIterator rows = DatabaseManager.queryTable(myContext, "shoppingcart", "SELECT * FROM shoppingcart WHERE journal = '"+ journal_name + "';");
			log.info(LogManager.getHeader(myContext, "select journal name ", " journal_name " + journal_name));
			try{
				    Map<String, Map<String,String>> article_map = new HashMap<String, Map<String,String>>();
					List<TableRow> propertyRows = rows.toList();
					for (int i = 0; i < propertyRows.size(); i++)
					{
							TableRow row = (TableRow) propertyRows.get(i);
							if(row.getDateColumn("payment_date")!=null){
								    log.info("payment_date: " + row.getDateColumn("payment_date"));
									long pay_date = row.getDateColumn("payment_date").getTime();
									if(date_util.isThisWeek(pay_date)){
											Map<String, String> map = new HashMap<String, String>();
											int item_id = row.getIntColumn("item");
											count_archived++;
											String key = get_doi(myContext,item_id);
											map.put("author",get_author(myContext,item_id));
											map.put("doi",key);
											map.put("manu_number",get_manu_number(myContext,item_id));
											if(key!=null)
													article_map.put(key,map);	
									}
							}
					}
                    article_archived.put(journal_name,article_map);                 

			}
			finally{
					if (rows != null)
					{
							rows.close();
					}
			}
		    return count_archived;		
	}
	public static int count_deposit_in_review(Context myContext, String journal_full_name) throws Exception{
				
		    int count_articles_in_review = 0;
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE item_id in (SELECT item_id FROM METADATAVALUE WHERE text_value='"+journal_full_name + "' and metadata_field_id=97) and text_value like '%start=Step: requiresReviewStep%';");
			try{
					List<TableRow> propertyRows =item_rows.toList();
				    Map<String, Map<String,String>> article_map = new HashMap<String, Map<String,String>>();
					for (int i = 0; i < propertyRows.size(); i++)
					{
							TableRow row = (TableRow) propertyRows.get(i);
							String text_value_str =row.getStringColumn("text_value");
							int start_index = text_value_str.indexOf("on 20");
							int end_index = text_value_str.indexOf(" workflow");
							String date = text_value_str.substring(start_index+3,end_index);
							DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
							Date format_date;
							try {
									format_date = df.parse(date);
									long date_long = format_date.getTime();
									if(date_util.isThisWeek(date_long)){
											int item_id = row.getIntColumn("item_id");
											Map<String, String> map = new HashMap<String, String>();
											count_articles_in_review ++;
											String key = get_doi(myContext,item_id);
											map.put("author",get_author(myContext,item_id));
											map.put("doi",key);
											map.put("manu_number",get_manu_number(myContext,item_id));
											if(key!=null)
													article_map.put(key,map);	
									}
							} catch (ParseException e) {
									e.printStackTrace();
							}	
					}
                    article_in_review.put(journal_full_name,article_map);                 


			}
			finally{
					if (item_rows != null)
					{
							item_rows.close();
					}
			}
            return count_articles_in_review;

	}
	public static int count_blackout(Context myContext, String journal_full_name) throws Exception{
				
		    int count_articles_blackout = 0;
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE item_id in (SELECT item_id FROM METADATAVALUE WHERE text_value='"+journal_full_name + "' and metadata_field_id=97) and text_value like '%action:afterPublicationAction Approved for entry into archive%';");
			try{
					List<TableRow> propertyRows =item_rows.toList();
					for (int i = 0; i < propertyRows.size(); i++)
					{
							TableRow row = (TableRow) propertyRows.get(i);
							String text_value_str =row.getStringColumn("text_value");
							int start_index = text_value_str.indexOf("on 20");
							int end_index = text_value_str.indexOf(" (GMT)");
							String date = text_value_str.substring(start_index+3,end_index);
							DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
							Date format_date;
							try {
									format_date = df.parse(date);
									long date_long = format_date.getTime();
									if(date_util.isThisWeek(date_long)){
											int item_id = row.getIntColumn("item_id");
											Map<String, String> map = new HashMap<String, String>();
											count_articles_blackout ++;
									}
							} catch (ParseException e) {
									e.printStackTrace();
							}	
					}


			}
			finally{
					if (item_rows != null)
					{
							item_rows.close();
					}
			}
            return count_articles_blackout;

	}
	public static String get_doi(Context myContext, int item_id) throws Exception{
			TableRowIterator rows = DatabaseManager.queryTable(myContext,"METADATAVALUE","SELECT * FROM METADATAVALUE where metadata_field_id=17 and item_id=" + item_id +" limit 1");
			String text_value_str = "the doi is not available";
			try{
					List<TableRow> propertyRows = rows.toList();
					for(int i = 0; i < propertyRows.size(); i++){

							TableRow row = (TableRow) propertyRows.get(i);
							text_value_str = row.getStringColumn("text_value");
					}


			}
			finally{
					if(rows!=null){
							rows.close();
					}
			}
			return text_value_str;

	}
	public static String get_manu_number(Context myContext, int item_id) throws Exception{
			TableRowIterator rows = DatabaseManager.queryTable(myContext,"METADATAVALUE","SELECT * FROM METADATAVALUE where metadata_field_id=74 and item_id=" + item_id +" limit 1");
			String text_value_str = "the manucsript number is not available";
			try{
					List<TableRow> propertyRows = rows.toList();
					for(int i = 0; i < propertyRows.size(); i++){

							TableRow row = (TableRow) propertyRows.get(i);
							text_value_str =row.getStringColumn("text_value");
					}


			}
			finally{
					if(rows!=null){
							rows.close();
					}
			}

			return text_value_str;
	}

	public static String get_author(Context myContext, int item_id) throws Exception{
			TableRowIterator rows = DatabaseManager.queryTable(myContext,"EPERSON","select * FROM eperson WHERE eperson_id=(select submitter_id from ITEM WHERE item_id="+item_id+");");
			String author_last_name = "the author is not available";
			try{
					List<TableRow> propertyRows = rows.toList();
					for(int i = 0; i < propertyRows.size(); i++){

							TableRow row = (TableRow) propertyRows.get(i);
							author_last_name = row.getStringColumn("lastname");
					}


			}
			finally{
					if(rows!=null){
							rows.close();
					}
			}
			return author_last_name;

	}
}
