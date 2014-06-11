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

import java.sql.SQLException;
import java.util.List;
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

    public static final String FULLNAME = "fullname";
	public static final String METADATADIR = "metadataDir";
    public static final String INTEGRATED = "integrated";
    public static final String NOTIFY_WEEKLY = "notifyWeekly";
    public static final String ALLOW_REVIEW_WORKFLOW = "allowReviewWorkflow";
    public static final String JOURNAL_ID = "journalID";
    public static final String NUMBER_ACCEPTED = "number_accepted";
    public static final String NUMBER_IN_REVIEW = "number_in_review";
    public static final String NUMBER_DEPOSITS_IN_REVIEW = "number_deposits_in_review";
    public static final String NUMBER_DEPOSITS_PUBLICATIONS = "number_deposits_publications";
    public static final String NUMBER_HIDDEN_TO_PUBLIC = "number_hidder_to_public";

    public static final String ACCEPTED = "<article_status>accepted</article_status>"; 
    public static final String SUBMITTED = "<article_status>submitted</article_status>"; 
    public static final String IN_REVIEW = "<article_status>in review</article_status>"; 
    public static final String UNDER_REVIEW = "<article_status>under review</article_status>"; 
    public static final String REVISION_IN_REVIEW = "<article_status>revision in review</article_status>"; 
 
    public static final java.util.Map<String, Map<String, String>> journalProperties = new HashMap<String, Map<String, String>>();
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
                if(properties.getProperty(str+INTEGRATED)!=null&&properties.getProperty(str+INTEGRATED).toLowerCase().equals("true")){
			
						Map<String, String> map = new HashMap<String, String>();
						String journal_dir = properties.getProperty(str + METADATADIR);
						//test output
						System.out.println(properties.getProperty(str+FULLNAME));
						map.put(FULLNAME, properties.getProperty(str + FULLNAME));
						map.put(NOTIFY_WEEKLY, properties.getProperty(str + NOTIFY_WEEKLY));
						map.put(JOURNAL_ID, journalType);
						map.put(NUMBER_ACCEPTED,String.valueOf(count_accept(journal_dir)));
						System.out.println("count_accept:" + count_accept(journal_dir));	
					     
						if(properties.getProperty(str+ALLOW_REVIEW_WORKFLOW)!=null){
								if(properties.getProperty(str+ALLOW_REVIEW_WORKFLOW).equals("true")){

										System.out.println("count_review:" + count_review(journal_dir));	
										map.put(NUMBER_IN_REVIEW,String.valueOf(count_review(journal_dir)));

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
           System.out.println("Error while loading journal properties");
        }

    }

	//count number of accepted publications
	public static int count_accept(String journal_dir){
			File journal_folder = new File(journal_dir);
			int count_accepted = 0;
            DateUtil date_util = new DateUtil();
			if(journal_folder.exists()&&journal_folder.isDirectory()&&journal_folder.list().length>0){
					File[] xmls = journal_folder.listFiles();
					for(File xml : xmls){
							if(!date_util.isThisWeek(xml.lastModified())){
									try{
											BufferedReader br = new BufferedReader(new FileReader(xml.getPath()));
											String line;
											while ((line = br.readLine()) != null) {
													// case-insensitive
													if(line.trim().toLowerCase().equals(ACCEPTED)){
															System.out.println(ACCEPTED);
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
						
						System.out.println("last modified: " + xml.lastModified());
							if(!date_util.isThisWeek(xml.lastModified())){
									try{
											BufferedReader br = new BufferedReader(new FileReader(xml.getPath()));
											String line;
											while ((line = br.readLine()) != null) {
													System.out.println(line.trim().toLowerCase());
													// case-insensitive
													String line_str = line.trim().toLowerCase();
													if(line_str.equals(SUBMITTED) || line_str.equals(IN_REVIEW) ||line_str.equals(UNDER_REVIEW) || line_str.equals(REVISION_IN_REVIEW)){
															System.out.println("sub");
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
	public static int count_deposit_publications(Context myContext, String journal_name) throws SQLException{
			int count_deposit_publication = 0;
			TableRowIterator rows = DatabaseManager.queryTable(myContext, "shoppingcart", "SELECT * FROM shoppingcart WHERE journal = ' "+ journal_name + "'");
			try{
					List<TableRow> propertyRows = rows.toList();
					System.out.println(propertyRows.size());
					for (int i = 0; i < propertyRows.size(); i++)
					{
							DateUtil date_util = new DateUtil();
							TableRow row = (TableRow) propertyRows.get(i);
							System.out.println(row.getDateColumn("payment_date"));
							System.out.println(row.getStringColumn("status"));
							if(!date_util.isThisWeek(row.getDateColumn("payment_date").getTime())){
								count_deposit_publication++;
							}
					}


			}
			finally{
					if (rows != null)
					{
							rows.close();
					}
			}
			return count_deposit_publication;		
	}
	public static void sendEmail(Context myContext) throws Exception{
			for(Map.Entry<String,Map<String,String>> journal : journalProperties.entrySet()){
					Map<String,String> journal_entry = journal.getValue();
					String host = ConfigurationManager.getProperty("dspace.hostname");
					String basicHost = "";
					if ("localhost".equals(host) || "127.0.0.1".equals(host)
									|| host.equals(InetAddress.getLocalHost().getHostAddress()))
					{
							basicHost = host;
					}
					else
					{
							// cut off all but the hostname, to cover cases where more than one URL
							// arrives at the installation; e.g. presence or absence of "www"
							int lastDot = host.lastIndexOf('.');
							basicHost = host.substring(host.substring(0, lastDot).lastIndexOf('.'));
					}
	
				Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(myContext.getCurrentLocale(), "integration_report"));;
				
				//add recipient
				String recipents = journal_entry.get(NOTIFY_WEEKLY);
				if(recipents.contains(",")){
						String[] recipent_arr = recipents.split(",");
						for(int i = 0; i < recipent_arr.length;i++){
								email.addRecipient(recipent_arr[i].trim());
						}
				}
				else{
						email.addRecipient(recipents.trim());
				}
				
				//set subject
				String subject = "Dryad/" + journal_entry.get(FULLNAME) + " integration report";

				email.addArgument(new Date());

				//set content
				email.addArgument(journal_entry.get(NUMBER_IN_REVIEW));
				email.addArgument("test");
				email.addArgument("test");
				email.addArgument("test");
				email.addArgument(subject);

				try
				{
						email.send();
				}
				catch (MessagingException me)
				{
						System.err.println("\nError sending email:");
						System.err.println(" - Error: " + me);
						System.err.println("\nPlease see the DSpace documentation for assistance.\n");
						System.err.println("\n");
						System.exit(1);
				}
				System.out.println("\nEmail sent successfully!\n");

		}


	}
}
