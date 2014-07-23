
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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

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
public class WeeklyCurationReport{
    
    private static Logger log = Logger.getLogger(IntegrationReport.class);
    public static final String FULLNAME = "fullname";
    public static final String INTEGRATED = "integrated";
    public static int NUMBER_INTE_ARCHIVED;
	public static int NUMBER_NON_INTE_ARCHIVED;
	public static int NUMBER_IN_REVIEW;
    public static int NUMBER_HIDDEN_TO_PUBLIC;
    public static int NUMBER_INTE_BLACKOUT;
    public static int NUMBER_NON_INTE_BLACKOUT;
    public static int NUMBER_INTE_DEPOSITS;
    public static int NUMBER_NON_INTE_DEPOSITS;
    public static DateUtil date_util = new DateUtil();

    //the hashmao stores all integrated journals' properities
    public static final java.util.Map<String, Map<String, String>> inte_journalProperties = new HashMap<String, Map<String, String>>();
   
    //the hashmap stores information of all non-integrated journals' archived submissions number 
    public static final java.util.Map<String, Integer> non_inte_journal_archived = new HashMap<String, Integer>();
    public static final java.util.Map<String, Integer> non_inte_journal_blackout = new HashMap<String, Integer>();

    //the hashmap stores number of journals from hidden to public submissions
    public static final java.util.Map<String, Integer> journal_hidden_to_public = new HashMap<String, Integer>();

    //the hashmap stores number of journals' archived 
    public static final java.util.Map<Integer,String> article_archived= new HashMap<Integer,String>();
	public static void main(String[] args) throws Exception{

        String journalPropFile = ConfigurationManager.getProperty("submit.journal.config");
        Properties properties = new Properties();
		Context myContext = new Context();
		NUMBER_IN_REVIEW = 0;
		NUMBER_NON_INTE_ARCHIVED = 0;
		NUMBER_INTE_ARCHIVED = 0;
		NUMBER_HIDDEN_TO_PUBLIC = 0;
		NUMBER_INTE_BLACKOUT = 0;
		NUMBER_NON_INTE_BLACKOUT = 0;
        NUMBER_INTE_DEPOSITS = 0;
        NUMBER_NON_INTE_DEPOSITS = 0;

        //commandLine parser are used to input start date and end date user want to count
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        options.addOption("f", "from", true, "Begin Date");
        options.addOption("t", "to", true, "End Date");
        CommandLine line = parser.parse(options, args);

        String  beginDate = null;
        String endDate = null;
        if(line.hasOption("f")) {
            beginDate = line.getOptionValue("f");
        }
        if(line.hasOption("t")) {
            endDate = line.getOptionValue("t");
        }
        try {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            Date start_date;
            Date end_date;
            start_date = df.parse(beginDate);
            end_date = df.parse(endDate);
            date_util.setStartTime(start_date.getTime());
            date_util.setEndTime(end_date.getTime());
            properties.load(new InputStreamReader(new FileInputStream(journalPropFile), "UTF-8"));
            String journalTypes = properties.getProperty("journal.order");
		    
			//read journal configuration file and get integrated journals
            for (int i = 0; i < journalTypes.split(",").length; i++) {
                String journalType = journalTypes.split(",")[i].trim();
                String str = "journal." + journalType + ".";
				log.debug("reading config for journal " + journalType);
				log.debug("fullname " + properties.getProperty(str + FULLNAME));
                if(properties.getProperty(str+INTEGRATED)!=null&&properties.getProperty(str+INTEGRATED).toLowerCase().equals("true")){
			
						Map<String, String> map = new HashMap<String, String>();
						map.put(FULLNAME, properties.getProperty(str + FULLNAME));
						String key = properties.getProperty(str + FULLNAME);
						if(key!=null&&key.length()>0){
								inte_journalProperties.put(key, map);
						}
				}

            }
            count_archived(myContext);
            count_blackout(myContext);
            count_in_review(myContext);
            count_hidden_to_public(myContext);
            NUMBER_INTE_DEPOSITS = NUMBER_INTE_ARCHIVED + NUMBER_INTE_BLACKOUT;
            NUMBER_NON_INTE_DEPOSITS = NUMBER_NON_INTE_ARCHIVED + NUMBER_NON_INTE_BLACKOUT;

			sendEmail(myContext);

        }catch (IOException e) {
				log.error("Error while loading journal properties", e);
		}

    }

    /*
     * counts number of all archived submissions and then call process_archived to judge whether the item is in which journal
     * the method read from METADATAVALUE table's provenance field.
     * Provenance field contains all the information about submission date, publication blackout information, archived date.
     */
	public static void count_archived(Context myContext) throws Exception{
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE text_value like '%Made available in DSpace%';");
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
									if(date_util.isThisPeriod(date_long)){
											int item_id = row.getIntColumn("item_id");
											process_archived(myContext,item_id);
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
    }

    /*
     * the method process each archived item and find the journal for each item.
     */
    public static void process_archived(Context myContext,int item_id) throws Exception{
		
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE metadata_field_id=97 and item_id=" + item_id + ";");
			try{
				List<TableRow> propertyRows = item_rows.toList();
				for(int i = 0; i < propertyRows.size(); i++){
						TableRow row = (TableRow) propertyRows.get(i);
						String journal_name = row.getStringColumn("text_value");
                        article_archived.put(item_id,journal_name);
						if(inte_journalProperties.containsKey(journal_name)){
								NUMBER_INTE_ARCHIVED++;
						}
						else{
								NUMBER_NON_INTE_ARCHIVED++;
								if(non_inte_journal_archived.containsKey(journal_name)){
										int num_archived = non_inte_journal_archived.get(journal_name) + 1;
										non_inte_journal_archived.put(journal_name, num_archived);
								}
								else{
										non_inte_journal_archived.put(journal_name,1);
								}
						}
				
				}
			
			}
			finally{
					if (item_rows != null)
					{
							item_rows.close();
					}
			}		
	
	}

    /*
     * count number of submissions from hidden to public during a given period.
     * The method will get all items from hidden to public during the period, and then call process_hidden to public to process each item
     */
	public static void count_hidden_to_public(Context myContext) throws Exception{
				
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE text_value like '%action:afterPublicationAction Approved for entry into archive%';");
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
									if(date_util.isThisPeriod(date_long)){
											NUMBER_HIDDEN_TO_PUBLIC++;
											int item_id = row.getIntColumn("item_id");
											process_hidden_to_public(myContext,item_id);
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

	}

    /*
     * the method read item_id from count_hidden_to_public method and find the joural for each item
     */
	public static void process_hidden_to_public(Context myContext,int item_id) throws Exception{
		
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE metadata_field_id=97 and item_id=" + item_id + ";");
			try{
				List<TableRow> propertyRows = item_rows.toList();
				for(int i = 0; i < propertyRows.size(); i++){
						TableRow row = (TableRow) propertyRows.get(i);
						String journal_name = row.getStringColumn("text_value");
						if(journal_hidden_to_public.containsKey(journal_name)){
								int num_hidden_to_pub = non_inte_journal_archived.get(journal_name) + 1;
								journal_hidden_to_public.put(journal_name, num_hidden_to_pub);
						}
						else{
								journal_hidden_to_public.put(journal_name,1);
						}
				
				}
			
			}
			finally{
					if (item_rows != null)
					{
							item_rows.close();
					}
			}
			
	
	}
	
    /* The current method doesn't work well, it just count the submissions in review
     */
    public static void count_in_review(Context myContext) throws Exception{
				
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE text_value like '%start=Step: requiresReviewStep%';");
			try{
					List<TableRow> propertyRows =item_rows.toList();
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
									if(date_util.isThisPeriod(date_long)){
											NUMBER_IN_REVIEW++;
											int item_id = row.getIntColumn("item_id");
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

	}

    /*
     * the method count all submissions come into publication blackout and call process_backout to deal with each item
     */
	public static void count_blackout(Context myContext) throws Exception{
				
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE text_value like '%Entered publication blackout%';");
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
									if(date_util.isThisPeriod(date_long)){
											int item_id = row.getIntColumn("item_id");
                                                if(!article_archived.containsKey(item_id)){
                                                    process_blackout(myContext,item_id);
                                                }
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

	}

    /*
     * the method process each item to find the item's journal
     */
	public static void process_blackout(Context myContext,int item_id) throws Exception{
	
	
			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE metadata_field_id=97 and item_id=" + item_id + ";");
			try{
				List<TableRow> propertyRows = item_rows.toList();
				for(int i = 0; i < propertyRows.size(); i++){
						TableRow row = (TableRow) propertyRows.get(i);
						String journal_name = row.getStringColumn("text_value");
						if(inte_journalProperties.containsKey(journal_name)){
								NUMBER_INTE_BLACKOUT++;
						}
						else{
								NUMBER_NON_INTE_BLACKOUT++;
								if(non_inte_journal_blackout.containsKey(journal_name)){
										int num_blackout = non_inte_journal_blackout.get(journal_name) + 1;
										non_inte_journal_blackout.put(journal_name, num_blackout);
								}
								else{
										non_inte_journal_blackout.put(journal_name,1);
								}
						}
				
				}
			
			}
			finally{
					if (item_rows != null)
					{
							item_rows.close();
					}
			}
			
	
	}

    /*
     * the method return a boolean whether a journal is new journal
     * it will find all items for the journal, if all items came this period, it will return true
     * else, it will return false
     */
	public static boolean is_new_journal(Context myContext, String journal_name) throws Exception{
		
			boolean is_new = true;

			TableRowIterator item_rows = DatabaseManager.queryTable(myContext, "METADATAVALUE", "SELECT * FROM METADATAVALUE WHERE item_id in (SELECT item_id FROM METADATAVALUE WHERE text_value='"+journal_name + "' and metadata_field_id=97) and text_value like '%start=Step: requiresReviewStep%';");
			try{
					List<TableRow> propertyRows =item_rows.toList();
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
									if(!date_util.isThisPeriod(date_long)){
										    is_new = false;
											return  false;
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
			return is_new;
	}
	
    //send a html email to curator
    //now the current receive person is yyhao1@gmail.com
    public static void sendEmail(Context myContext) throws Exception{
	
				Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(myContext.getCurrentLocale(), "WeeklySummaryReport"));;
				
				//add recipient
				String recipent = "yyhao1@gmail.com";
				email.addRecipient(recipent);
				
				//set subject
				String subject = "Weekly Curation Summary";

				email.addArgument(new Date());
                email.addArgument(NUMBER_INTE_DEPOSITS);
                email.addArgument(NUMBER_INTE_BLACKOUT);
                email.addArgument(NUMBER_NON_INTE_DEPOSITS);
                email.addArgument(NUMBER_NON_INTE_BLACKOUT);
                email.addArgument(NUMBER_IN_REVIEW);
                email.addArgument(NUMBER_HIDDEN_TO_PUBLIC);

                String non_inte_to_blackout = "";
                for(Map.Entry<String,Integer> entry: non_inte_journal_blackout.entrySet()){
                    String key = entry.getKey();
                    if(is_new_journal(myContext,key)){
                        non_inte_to_blackout +="<p style=\"color:#FF69B4\">";
                    }
                    else{
                        non_inte_to_blackout +="<p>";
                    }
                    non_inte_to_blackout +=key;
                    if(entry.getValue()!=1){
                        String sub_number = Integer.toString(entry.getValue());
                        String add_info = "(" + sub_number + " submissions ) ";
                        non_inte_to_blackout += add_info;
                    }
                    non_inte_to_blackout += "</p>";
                }
                email.addArgument(non_inte_to_blackout);
                String non_inte_to_archived = "";
                for(Map.Entry<String,Integer> entry: non_inte_journal_archived.entrySet()){
                    String key = entry.getKey();
                    if(is_new_journal(myContext,key)){
                        non_inte_to_archived +="<p style=\"color:#FF69B4\">";
                    }
                    else{
                        non_inte_to_archived +="<p>";
                    }
                    non_inte_to_archived +=key;
                    if(entry.getValue()!=1){
                        String sub_number = Integer.toString(entry.getValue());
                        String add_info = "(" + sub_number + " submissions ) ";
                        non_inte_to_archived += add_info;
                    }
                    non_inte_to_archived += "</p>";
                }
                email.addArgument(non_inte_to_archived);
                String hidden_to_public = "";
                for(Map.Entry<String,Integer> entry: journal_hidden_to_public.entrySet()){
                    String key = entry.getKey();
                    if(is_new_journal(myContext,key)){
                        hidden_to_public +="<p style=\"color:#FF69B4\">";
                    }
                    else{
                        hidden_to_public +="<p>";
                    }
                    hidden_to_public +=key;
                    if(entry.getValue()!=1){
                        String sub_number = Integer.toString(entry.getValue());
                        String add_info = "(" + sub_number + " submissions ) ";
                        hidden_to_public += add_info;
                    }
                    hidden_to_public += "</p>";
                }
                email.addArgument(hidden_to_public);
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