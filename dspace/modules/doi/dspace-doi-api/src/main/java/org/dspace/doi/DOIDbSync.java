package org.dspace.doi;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.content.crosswalk.QDCCrosswalk;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.identifier.*;
import org.dspace.identifier.DOI;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.utils.DSpace;
import org.dspace.versioning.Version;
import org.dspace.versioning.VersionHistory;
import org.dspace.versioning.VersioningService;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: fabio.bolognesi
 * Date: 8/15/11
 * Time: 2:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class DOIDbSync {


      public static void main(String[] args) {

        // check
        if(args==null || args.length==0 || args.length > 1 || (!args[0].equals("-s") && !args[0].equals("-r")) ){
                System.out.println("-s: to synchronize the database");
                System.out.println("-r: to create a database report");
                return;
        }

        String myDataPkgColl = ConfigurationManager.getProperty("stats.datapkgs.coll");
        String myDataFilesColl = ConfigurationManager.getProperty("stats.datafiles.coll");

        File output = new File("doidbsynch_output.txt");
        FileOutputStream outputStream=null;
        PrintStream ps = null;

        try{
            DOIIdentifierService dis = new DOIIdentifierService();
            Context context = new Context();
            context.turnOffAuthorisationSystem();
            ItemIterator itemIterator = Item.findAll(context);

            int itemCounter=0, itemResolvedCounter=0, itemNotResolvedCounter=0, itemNotArchived=0;
            outputStream = new FileOutputStream(output);
            ps = new PrintStream(outputStream);

            while(itemIterator.hasNext()){
                Item item = itemIterator.next();

                if(item.getOwningCollection()!=null && item.getOwningCollection().getHandle()!=null
                        && (item.getOwningCollection().getHandle().equals(myDataPkgColl) || item.getOwningCollection().getHandle().equals(myDataFilesColl))){

                    itemCounter++;


                    DCValue[] identifier = item.getMetadata("dc.identifier");
                    String dc_identifier=null;
                    if(identifier!=null && identifier.length > 0){
                        dc_identifier=identifier[0].value;
                    }

                    if(item.isArchived() && dc_identifier!=null){
                        ps.print("START ITEM");
                        ps.println();

                        if(!resolve(context, item, dis, ps, dc_identifier)){
                            itemNotResolvedCounter++;
                            if(islookupOk(context, item, dis, ps, dc_identifier)){

                                if(args[0].equals("-s")){
                                    DOI newDoi = mint(context, item, item.getOwningCollection().getHandle(), false, dc_identifier, ps);
                                    ps.print("new DOI added: " + newDoi.toString() + " " + newDoi.getTargetURL().toString());
                                    ps.println();
                                }else{
                                    DOI newDoi = mint(context, item, item.getOwningCollection().getHandle(), true, dc_identifier, null);
                                    ps.print("new DOI that would be added: " + newDoi.toString() + " " + newDoi.getTargetURL().toString());
                                    ps.println();
                                }
                            }
                        }
                        else itemResolvedCounter++;
                        ps.print("END ITEM");
                        ps.println();

                    } else itemNotArchived++;
                }
            }
            System.out.println("Item Examined: " + itemCounter + "   ItemResolved: " + itemResolvedCounter + "   ItemNotResolved: " + itemNotResolvedCounter + "   ItemNotArchived: " + itemNotArchived);
            ps.print("Item Examined: " + itemCounter + "   ItemResolved: " + itemResolvedCounter + "   ItemNotResolved: " + itemNotResolvedCounter + "   ItemNotArchived: " + itemNotArchived);

            System.out.println("LOOK AT doidbsynch_output.txt");

            context.abort();
        }catch (Exception ex) {
            ex.printStackTrace();
        }finally{
            try {
                if(ps!=null) ps.close();
                if(outputStream!=null) outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }


    }


    private static boolean resolve(Context context, Item item, DOIIdentifierService dis, PrintStream ps, String dc_identifier) throws IOException {

        DSpaceObject dso = dis.resolve(context, dc_identifier);
        if(dso!=null){
            ps.print("Item: " + item.getID() + "  " + dc_identifier + "  " + item.getHandle() + " Resolved.");
            ps.println();
            return true;
        }else{
           ps.print("Item: " + item.getID() + "  " + dc_identifier + "  " + item.getHandle() + " Not Resolved.");
            ps.println();
        }
        return false;
    }


    /*
     return false if there are some issue, it the DOI is just missing from the DB return true.
     */
    private static boolean islookupOk(Context context, Item item, DOIIdentifierService dis, PrintStream ps, String dc_identifier) throws SQLException, IOException {

        String handle = dis.lookup(dc_identifier);
        if(handle!=null && !"".equals(handle)){
            ps.print("ATTENTION! For this Identifier we have registered the following handle: " + handle);
            ps.println();
            return false;
        }
        else{
            ps.print("For this Identifier we don't have any handle registered.");
            ps.println();
        }

        if(item.getHandle()!=null){
            String url= HandleManager.resolveToURL(context, item.getHandle()).toString();
            String doi_id = dis.lookupByURL(url);
            if(doi_id!=null && !"".equals(doi_id)){
                ps.print("ATTENTION! For this handle we have registered the following DOI: " + doi_id);
                ps.println();
                return false;
            }
            else{
                ps.print("For this handle we don't have any DOI registered.");
                ps.println();
            }
        }
        //ps.print("The handle is null");
        ps.println();
        return true;
    }



    private static DOI mint(Context context, Item item, String collection, boolean isTest, String dc_identifier, PrintStream ps) throws SQLException, IOException {
        VersioningService versioningService = new DSpace().getSingletonService(VersioningService.class);
        org.dspace.versioning.VersionHistory history = versioningService.findVersionHistory(context, item.getID());
        String url=HandleManager.resolveToURL(context, item.getHandle()).toString();

        DOI doi_=null;

        //look if it is a versioned item..
        if(history !=null && !history.isEmpty()){

            DOI canonical = getCanonical(collection, dc_identifier, url);
            // if it is the canonical: take the handle of last version and assign it to this DOI
            if(dc_identifier.toString().equals(canonical.toString())){
                String handleLastVersion = history.getLatestVersion().getItem().getHandle();
                url=HandleManager.resolveToURL(context, handleLastVersion).toString();
                doi_ = new DOI(dc_identifier, new URL(url));
            }
            // if it is the last version: re-mint the canonical make it pointing to the last handle
            else if(history.getLatestVersion().getItem().equals(item)){
                String handleLastVersion = history.getLatestVersion().getItem().getHandle();
                url=HandleManager.resolveToURL(context, handleLastVersion).toString();
                doi_ = new DOI(canonical.toString(), new URL(url));
            }
            else{
               doi_ = new DOI(dc_identifier, new URL(url));
            }
        }else{
            doi_ = new DOI(dc_identifier, new URL(url));
        }

        if(doi_!=null && !isTest){
            String urlString = ConfigurationManager.getProperty("doi.service.url") + "?item=" + doi_.getTargetURL().toString() + "&doi=" + doi_.toString();
            ps.println("Servlet invocation: " + urlString);
            if(ps!=null){
                ps.println("Servlet response: " + getUrlResponse(urlString));
                ps.println();
            }
        }
        return doi_;
    }




    private static String getUrlResponse(String urlString) throws IOException {
        URL doiRequestUrl = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) doiRequestUrl.openConnection();
        //Make sure we get a result
        connection.setDoOutput(true);
        connection.connect();
        try {
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String result = "";
                InputStream inpuStream = connection.getInputStream();
                result = inputStreamAsString(inpuStream);
                if (result != null)
                    result = result;

                return result;
            }
        } finally {
            connection.disconnect();
        }
        return null;
    }

    public static String inputStreamAsString(InputStream stream) throws IOException {
        BufferedReader BR = new BufferedReader(new InputStreamReader(stream));
        StringBuilder SB = new StringBuilder();
        String line;

        while ((line = BR.readLine()) != null) {
            SB.append(line);
        }

        BR.close();
        return SB.toString();
    }


     private static DOI getCanonical(String collection, String doi_identifier, String url) throws MalformedURLException {
        String myDataPkgColl = ConfigurationManager.getProperty("stats.datapkgs.coll");
        String myDataFilesColl = ConfigurationManager.getProperty("stats.datafiles.coll");
        DOI canonical = null;
        if (collection.equals(myDataPkgColl)) {
            canonical = getCanonicalDataPackage(doi_identifier, url);
        } else {
            canonical = getCanonicalDataFile(doi_identifier, url);
        }
        return canonical;
    }


    private static final char DOT = '.';
    private static final char SLASH = '/';

    private static DOI getCanonicalDataPackage(String doi, String url) throws MalformedURLException {
        String canonicalID = doi.toString().substring(0, doi.toString().lastIndexOf(DOT));
        DOI canonical = new DOI(canonicalID, new URL(url));
        return canonical;
    }

    private static DOI getCanonicalDataFile(String doi, String url) throws MalformedURLException {
        String idDP = doi.substring(0, doi.toString().lastIndexOf(SLASH));
        String idDF = doi.substring(doi.toString().lastIndexOf(SLASH) + 1);


        String canonicalDP = idDP.substring(0, idDP.lastIndexOf(DOT));
        String canonicalDF = idDF.substring(0, idDF.lastIndexOf(DOT));


        DOI canonical = new DOI(canonicalDP + SLASH + canonicalDF, new URL(url));
        return canonical;
    }


    private static short countDots(String doi){
        short index=0;
        int indexDot=0;
        while( (indexDot=doi.indexOf('.'))!=-1){
            doi=doi.substring(indexDot+1);
            index++;
        }
        return index;
    }

}
