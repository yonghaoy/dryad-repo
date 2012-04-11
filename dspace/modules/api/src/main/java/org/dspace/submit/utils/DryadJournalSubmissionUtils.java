package org.dspace.submit.utils;

import org.apache.log4j.Logger;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.workflow.DryadWorkflowUtils;
import org.dspace.workflow.WorkflowItem;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: fabio.bolognesi
 * Date: 9/7/11
 * Time: 9:47 AM
 * To change this template use File | Settings | File Templates.
 */
public class DryadJournalSubmissionUtils {
    private static Logger log = Logger.getLogger(DryadJournalSubmissionUtils.class);

    // Reading DryadJournalSubmission.properties
    public static final String FULLNAME = "fullname";
    public static final String METADATADIR = "metadataDir";
    public static final String INTEGRATED = "integrated";
    public static final String PUBLICATION_BLACKOUT = "publicationBlackout";
    public static final String NOTIFY_ON_REVIEW = "notifyOnReview";
    public static final String NOTIFY_ON_ARCHIVE = "notifyOnArchive";
    public static final String JOURNAL_ID = "journalID";


    public static final java.util.Map<String, Map<String, String>> journalProperties = new HashMap<String, Map<String, String>>();
    static{
        String journalPropFile = ConfigurationManager.getProperty("submit.journal.config");
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(journalPropFile));
            String journalTypes = properties.getProperty("journal.order");

            for (int i = 0; i < journalTypes.split(",").length; i++) {
                String journalType = journalTypes.split(",")[i].trim();

                String str = "journal." + journalType + ".";

                Map<String, String> map = new HashMap<String, String>();
                map.put(FULLNAME, properties.getProperty(str + FULLNAME));
                map.put(METADATADIR, properties.getProperty(str + METADATADIR));
                map.put(INTEGRATED, properties.getProperty(str + INTEGRATED));
                map.put(PUBLICATION_BLACKOUT, properties.getProperty(str + PUBLICATION_BLACKOUT, "false"));
                map.put(NOTIFY_ON_REVIEW, properties.getProperty(str + NOTIFY_ON_REVIEW));
                map.put(NOTIFY_ON_ARCHIVE, properties.getProperty(str + NOTIFY_ON_ARCHIVE));
                map.put(JOURNAL_ID, journalType);

                String key = properties.getProperty(str + FULLNAME);
                journalProperties.put(key, map);
            }

        }catch (IOException e) {
            log.error("Error while loading journal properties", e);
        }
    }


    public static boolean isJournalBlackedOut(Context context, Item item, Collection collection) throws SQLException {
        // get Journal
        Item dataPackage=item;
        if(!isDataPackage(collection))
            dataPackage = DryadWorkflowUtils.getDataPackage(context, item);


        DCValue[] journalFullNames = dataPackage.getMetadata("prism.publicationName");
        String journalFullName=null;
        if(journalFullNames!=null && journalFullNames.length > 0){
            journalFullName=journalFullNames[0].value;
        }

        // show "Publish immediately" only if publicationBlackout=false or not defined in DryadJournalSubmission.properties.
        Map<String, String> values = journalProperties.get(journalFullName);

        String isBlackedOut = null;
        if(values!=null && values.size()>0)
            isBlackedOut = values.get(PUBLICATION_BLACKOUT);
        if(isBlackedOut==null || isBlackedOut.equals("false"))
            return false;
        return true;
    }


     private static boolean isDataPackage(Collection coll) throws SQLException {
        return coll.getHandle().equals(ConfigurationManager.getProperty("submit.publications.collection"));
    }


    public static String findKeyByFullname(String fullname){
        Map<String, String> props = journalProperties.get(fullname);
        if(props!=null)
            return props.get(DryadJournalSubmissionUtils.JOURNAL_ID);

        return null;
    }
}