package org.dspace.identifier;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Field;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.utils.DSpace;
import org.dspace.versioning.Version;
import org.dspace.versioning.VersionHistory;
import org.dspace.versioning.VersioningService;
import org.springframework.stereotype.Component;

import javax.swing.text.StyleContext;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.sql.SQLException;

/**
 * User: kevin (kevin at atmire.com)
 * Date: 20-dec-2010
 * Time: 14:09:07
 * <p/>
 * The identifier service implementation using the NESCent doi webservices
 * This class will register a doi identifier for an item and can also be used to retrieve an item by its doi.
 */
@Component
public class DOIIdentifierService implements DSpaceIdentifierService {

    private static Logger log = Logger.getLogger(HandleIdentifierService.class);
    private static DCValue identifierMetadata = new DCValue();

    private DryadDOIMinter minter = new DryadDOIMinter();

    public DOIIdentifierService() {
        identifierMetadata.schema = MetadataSchema.DC_SCHEMA;
        identifierMetadata.element = "identifier";
        identifierMetadata.qualifier = null;
    }

    public String register(Context context, DSpaceObject dso) throws IdentifierException {
        try {
            if (dso instanceof Item && dso.getHandle() != null) {
                String doi = getDoiValue((Item) dso);
                doi = mintAndRegister(context, (Item) dso, doi, true);
                ((Item) dso).clearMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null);
                ((Item) dso).addMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null, doi);
            }
        } catch (Exception e) {
            log.error(LogManager.getHeader(context, "Error while attempting to register doi", "Item id: " + dso.getID()));
            throw new IdentifierException("Error while registering doi identifier", e);
        }
        return null;

    }

    public String mint(Context context, DSpaceObject dso) throws IdentifierException {
        try {
            if (dso instanceof Item && dso.getHandle() != null) {
                String doi = getDoiValue((Item) dso);
                doi = mintAndRegister(context, (Item) dso, doi, false);
                ((Item) dso).clearMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null);
                ((Item) dso).addMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null, doi);
            }
        } catch (Exception e) {
            log.error(LogManager.getHeader(context, "Error while attempting to mint doi", "Item id: " + dso.getID()));
            throw new IdentifierException("Error while retrieving doi identifier", e);
        }
        return null;
    }


    public void delete(Context context, DSpaceObject dso) throws IdentifierException {
        try {
            if (dso instanceof Item && dso.getHandle() != null) {
                Item item = (Item) dso;
                String doi = getDoiValue((Item) dso);

                // Remove from DOI service only if the item is not registered
                // if it is already registered it has to remain in DOI service ("tombstone")
                if(!item.isArchived())
                    remove(doi.toString());


                // If it is the most current version occurs to move the canonical to the previous version
                VersionHistory history = minter.retrieveVersionHistory(context, item);
                if(history.getLatestVersion().getItem().equals(item) && history.size() > 1){
                    Item previous = history.getPrevious(history.getLatestVersion()).getItem();

                    String urlString = HandleManager.resolveToURL(context, previous.getHandle()).toString();
                    URL url = new URL(urlString);
                    DOI doi_ = new DOI(doi, url);
                    String collection = minter.getCollection(context, item);
                    String myDataPkgColl = ConfigurationManager.getProperty("stats.datapkgs.coll");
                    DOI canonical=null;
                    if (collection.equals(myDataPkgColl)) {
                        canonical = minter.getCanonicalDataPackage(doi_);
                    } else {
                        canonical = minter.getCanonicalDataFile(doi_);

                    }
                    String canonicalDoi = mint(canonical, true);
                }
            }
        } catch (Exception e) {
            log.error(LogManager.getHeader(context, "Error while attempting to register doi", "Item id: " + dso.getID()));
            throw new IdentifierException("Error while moving doi identifier", e);
        }
    }


    private String mintAndRegister(Context context, Item item, String doi, boolean register) throws Exception {

        String collection = minter.getCollection(context, item);
        String myDataPkgColl = ConfigurationManager.getProperty("stats.datapkgs.coll");
        VersionHistory history = minter.retrieveVersionHistory(context, item);
        String url=HandleManager.resolveToURL(context, item.getHandle()).toString();

        // CASE A:  it is a versioned datafile and the user is modifying its content (adding or removing bitstream) upgrade version number.
        if(item.isArchived()){
            if(!collection.equals(myDataPkgColl)){
                if(lookup(doi)!=null){
                    DOI doi_= minter.upgradeDOIDataFile(context, url, doi, item, history);
                    if(doi_!=null){
                        remove(doi);
                        doi = mint(doi_, register);
                        item.clearMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null);                        
                        item.update();                        
                        if (doi == null || doi.equals("")) throw new Exception();
                    }
                }
            }
        }

        // CASE B: New Item  or New version
        // FIRST time a VERSION is created 2 identifiers will be minted  and the canonical will be updated to point to the newer URL:
        //  - id.1-->old URL
        //  - id.2-->new URL
        //  - id(canonical)-- new URL
        // Next times 1 identifier will be minted  and the canonical will be updated to point to the newer URL
        //  - id.x-->new URL
        //  - id(canonical)-- new URL
        // If it is a new ITEM just 1 identifier will be minted

        else{ // only if it is in workflow.
            DOI doi_ = null;
            // MINT Identifier || .x
            doi_ = minter.calculateDOI(context, doi, url, item, history);
            doi = mint(doi_, register);
            if (doi == null || doi.equals("")) throw new Exception();


            Version version = minter.getVersion(context, item);
            if (version != null) {

                // if it is the first time that is called "create version": mint identifier ".1"
                Version previous = history.getPrevious(version);
                if (history.isFirstVersion(previous)) {
                    DOI firstDOI = minter.calculateDOIFirstVersion(context, previous);
                    mint(firstDOI, register);
                    if (firstDOI == null || firstDOI.equals("")) throw new Exception();
                }

                // move the canonical
                DOI canonical = null;
                if (collection.equals(myDataPkgColl)) {
                    canonical = minter.getCanonicalDataPackage(doi_);
                } else {
                    canonical = minter.getCanonicalDataFile(doi_);

                }

                String canonicalDoi = mint(canonical, register);
                if (canonicalDoi == null || canonicalDoi.equals("")) throw new Exception();


            }
        }
        return doi;
    }


    private String mint(DOI doi, boolean register) throws IOException {
        String urlString = ConfigurationManager.getProperty("doi.service.url") + "?item=" + URLEncoder.encode(doi.getTargetURL().toString(), "UTF8") + "&doi=" + doi.toString();

        if (ConfigurationManager.getBooleanProperty("doi.service.register", register)) {
            urlString += "&register";
        }
        return getUrlResponse(urlString);
    }


    private String delete(String doi) throws IOException {
        String urlString = ConfigurationManager.getProperty("doi.service.url") + "?remove=" + doi;
        return getUrlResponse(urlString);
    }


    /**
     * Returns the doi value in the metadata (if present, else null will be returned)
     *
     * @param item the item to check for a doi
     * @return the doi string
     */
    public static String getDoiValue(Item item) {
        DCValue[] doiVals = item.getMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, Item.ANY);
        if (doiVals != null && 0 < doiVals.length) {
            return doiVals[0].value;
        }
        return null;

    }

    public DSpaceObject resolve(Context context, String identifier, String... attributes) {
        //Check if we really have a doi identifier
        try {
            if (identifier != null && identifier.startsWith("doi:")) {
                String urlString = ConfigurationManager.getProperty("doi.service.url") + "?lookup=" + identifier;
                String handle;
                if (ConfigurationManager.getBooleanProperty("doi.service.testmode", false)) {
                    handle = identifier.replace("doi:", "");
                } else {
                    handle = getUrlResponse(urlString);
                }
                if (handle != null) {
                    return HandleManager.resolveUrlToDSpaceObject(context, handle);
                }
            }
        } catch (Exception e) {
            log.error(LogManager.getHeader(context, "Error while attempting to resolve doi", "Identifier: " + identifier));
        }

        return null;
    }

    public String lookup(String identifier) {
        //Check if we really have a doi identifier
        try {
            if (identifier != null && identifier.startsWith("doi:")) {
                String urlString = ConfigurationManager.getProperty("doi.service.url") + "?lookup=" + identifier;
                String handle;
                if (ConfigurationManager.getBooleanProperty("doi.service.testmode", false)) {
                    handle = identifier.replace("doi:", "");
                } else {
                    handle = getUrlResponse(urlString);
                }
                return handle;
            }
        } catch (Exception e) {
            log.error("Error while attempting to resolve doi :: Identifier: " + identifier);
        }
        return null;
    }


    public String lookupByURL(String url) {
        //Check if we really have a doi identifier
        try {
            if (url != null) {
                String urlString = ConfigurationManager.getProperty("doi.service.url") + "?lookup=1&lookupbyurl=" + url;
                return getUrlResponse(urlString);
            }
        } catch (Exception e) {
            log.error("Error while attempting to resolve url: " + url);
        }
        return null;
    }


    public String lookupURLByDOI(String identifier) {
        try {
            if (identifier != null && identifier.startsWith("doi:")) {
                String urlString = ConfigurationManager.getProperty("doi.service.url") + "?targeturl=" + identifier;
                String url;
                if (ConfigurationManager.getBooleanProperty("doi.service.testmode", false)) {
                    url = identifier.replace("doi:", "");
                } else {
                    url = getUrlResponse(urlString);
                }
                return url;
            }
        } catch (Exception e) {
            log.error("Error while attempting to resolve doi :: Identifier: " + identifier);
        }
        return null;

    }

    public String remove(String identifier) {
        //Check if we really have a doi identifier
        try {
            if (identifier != null && identifier.startsWith("doi:")) {
                String urlString = ConfigurationManager.getProperty("doi.service.url") + "?remove=" + identifier;
                String handle;
                if (ConfigurationManager.getBooleanProperty("doi.service.testmode", false)) {
                    handle = identifier.replace("doi:", "");
                } else {
                    handle = getUrlResponse(urlString);
                }
                return handle;
            }
        } catch (Exception e) {
            log.error("Error while attempting to resolve doi :: Identifier: " + identifier);
        }
        return null;
    }

    private String getUrlResponse(String urlString) throws IOException {
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

    public static DCValue getIdentifierMetadata() {
        return identifierMetadata;
    }


    /**
     * Created by IntelliJ IDEA.
     * User: fabio.bolognesi
     * Date: Jun 17, 2011
     * Time: 4:06:15 PM
     * To change this template use File | Settings | File Templates.
     */
    public class DryadDOIMinter {

        // Max number of files attached to a package; completely arbitrary
        private static final int MAX_NUM_OF_FILES = 150;

        private String myHdlPrefix;
        private String myHostname;
        private String myDataPkgColl;
        private String myDataFileColl;
        private String myLocalPartPrefix;
        private String myDoiPrefix;

        private int mySuffixVarLength = 8;
        private final SecureRandom myRandom = new SecureRandom();


        /**
         * Creates a DOI from the supplied DSpace URL string
         *
         * @param aDSpaceURL
         * @return
         */
        public DOI calculateDOI(Context context, String aDoi, String aDSpaceURL, Item item, VersionHistory vh) {
            URL itemURL;
            String url;
            DOI doi = null;

            myHdlPrefix = ConfigurationManager.getProperty("handle.prefix");
            myHostname = ConfigurationManager.getProperty("dryad.url");
            myDataPkgColl = ConfigurationManager.getProperty("stats.datapkgs.coll");
            myDataFileColl = ConfigurationManager.getProperty("stats.datafiles.coll");
            myDoiPrefix = ConfigurationManager.getProperty("doi.prefix");
            myLocalPartPrefix = ConfigurationManager.getProperty("doi.localpart.suffix");


            itemURL = getTarget(aDSpaceURL);
            log.debug("Checking to see if " + itemURL.toString() + " is in the DOI database");


            // FB Removed in favor of lookup doi = myLocalDatabase.getByURL(target.toString());
            // try to retrieve DOI from the DB
            doi = getDOI(aDoi, itemURL);

            // If our DOI doesn't exist, then we need to mint one
            if (doi == null) {
                log.debug("DOI wasn't found in the DOI database");
                try {
                    //context = new Context();
                    context.turnOffAuthorisationSystem();

                    String hdl[] = stripHandle(aDSpaceURL);

                    log.debug("Opening DSpace database connection (DSpace context)");

                    //Item item = (Item) HandleManager.resolveToObject(context, hdl[0]);
                    String collection = getCollection(context, item);

                    // DATAPACKAGE
                    if (collection.equals(myDataPkgColl)) {
                        doi = calculateDOIDataPackage(context, hdl[1], itemURL.toString(), item, vh);
                    }
                    // DATAFILE
                    else if (collection.equals(myDataFileColl)) {
                        doi = calculateDOIDataFile(itemURL, doi, item, vh);
                    }

                } catch (ClassCastException details) {
                    throw new RuntimeException(details);
                } catch (SQLException details) {
                    if (context != null) {
                        context.abort();
                    }
                    throw new RuntimeException(details);
                } catch (Exception details) {
                    throw new RuntimeException(details);
                } finally {
//                    try{
//                        if (context != null) {
//                            //context.complete();
//                        }
//
//                    }catch (SQLException details) {
//                        throw new RuntimeException(details);
//                    }
                }
            }
            return doi;
        }


        private DOI calculateDOIFirstVersion(Context c, Version previous) throws SQLException {
            DOI doi;
            String idDoi = getDoiValue(previous.getItem());
            String dspaceURL = HandleManager.resolveToURL(c, previous.getItem().getHandle());
            doi = new DOI(idDoi, getTarget(dspaceURL));
            return doi;
        }


        private DOI getDOI(String aDoi, URL itemURL) {
            DOI doi = null;
            if (aDoi == null) {
                String id = lookupByURL(itemURL.toString());
                if (id != null)
                    doi = new DOI(id, itemURL);
            } else {
                doi = new DOI(aDoi, itemURL);
                if (!exists(doi)) doi = null;
            }
            return doi;
        }


        private static final char DOT = '.';
        private static final char SLASH = '/';

        private synchronized DOI calculateDOIDataPackage(Context c, String suffix, String aTargetURL, Item item, org.dspace.versioning.VersionHistory history) throws IOException, IdentifierException, AuthorizeException, SQLException {
            DOI doi, oldDoi = null;

            // Versioning: if it is a new version of an existing Item, the new DOI must be: oldDOI.(versionNumber), retrieve previous Item
            if (history != null) {
                Version version = history.getVersion(item);
                Version previous = history.getPrevious(version);
                String previousDOI = getDoiValue(previous.getItem());

                // FIRST time a VERSION is created: update identifier of the previous item adding ".1"
                if (history.isFirstVersion(previous)) {
                    previousDOI=updateIdentierPreviousItem(previous.getItem());
                }

                String canonical = previousDOI.substring(0, previousDOI.lastIndexOf(DOT));
                String versionNumber = "" + DOT + (version.getVersionNumber());
                doi = new DOI(canonical + versionNumber);
            } else {
                String var = buildVar();
                doi = new DOI(myDoiPrefix, myLocalPartPrefix + var);

                if (existsIdDOI(doi.toString()))
                    return calculateDOIDataPackage(c, suffix, aTargetURL, item, history);
            }
            doi.setTargetURL(new URL(aTargetURL));
            return doi;
        }


        private boolean exists(DOI doi) {
            String dbDoiURL = lookup(doi.toString());

            if (doi.getTargetURL().toString().equals(dbDoiURL))
                return true;

            return false;
        }

        private DOI calculateDOIDataFile(URL target, DOI doi, Item item, VersionHistory history) throws IOException, IdentifierException, AuthorizeException, SQLException {
            DCValue[] pkgLink;
            String doiString;
            pkgLink = item.getMetadata("dc.relation.ispartof");

            if (pkgLink == null) {
                throw new RuntimeException("Not linked to a data package");
            }
            if (!(doiString = pkgLink[0].value).startsWith("doi:")) {
                throw new DOIFormatException("isPartOf value doesn't start with 'doi:'");
            }

            // Versioning: if it is a new version of an existing Item, the new DOI must be: oldDOI.(versionNumber)
            if (history != null) { // NEW VERSION OF AN EXISTING ITEM
                Version version = history.getVersion(item);
                Version previous = history.getPrevious(version);

                // FIRST time a VERSION is created: update identifier of the previous item adding ".1" before /
                if (history.isFirstVersion(previous)) {
                    updateIdentierPreviousDF(previous.getItem());
                }

                // mint NEW DOI: taking first part from id dataPackage father (until the /) + taking last part from id previous dataFile (after the slash)  e.g., 1111.3 / 1.1
                String idPrevious = getDoiValue(previous.getItem());
                String suffixDF = idPrevious.substring(idPrevious.lastIndexOf(SLASH) + 1);


                // the item has been modified? if yes: increment version number          
                DOI childDOI=null;
                if(countBitstreams(previous.getItem())!= countBitstreams(item)){
                    int versionN = Integer.parseInt(suffixDF.substring(suffixDF.lastIndexOf(DOT)+1));
                    childDOI = new DOI(doiString + "/" + suffixDF.substring(0, suffixDF.lastIndexOf(DOT)) + DOT  + (versionN+1), target);
                }
                else{
                    childDOI = new DOI(doiString + "/" + suffixDF, target);
                }
                return childDOI;
            }
            else { // NEW ITEM: mint a new DOI
                // has an arbitrary max; in reality much, much less
                for (int index = 1; index < MAX_NUM_OF_FILES; index++) {

                    // check if canonical already exists
                    String idDOI = getCanonicalDataPackage(doiString) + "/" + index;
                    if (existsIdDOI(idDOI)) {
                        String dbDoiURL = lookupURLByDOI(idDOI);
                        if (dbDoiURL.equals(target.toString())) {
                            return new DOI(doiString + "/" + index, dbDoiURL);
                        }
                    } else {
                        DOI childDOI = new DOI(doiString + "/" + index);
                        childDOI.setTargetURL(target);
                        return childDOI;
                    }
                }
            }
            return null;
        }

        /**
         * If a bitstream is added to or removed from the DataFile, we have to upgrade the version number
         * only if the item is already versioned and if it wasn't already upgraded.
         * @return
         */
        private DOI upgradeDOIDataFile(Context c, String target, String idDoi, Item item, VersionHistory history) throws SQLException, AuthorizeException {
            URL itemURL = getTarget(target);
            DOI doi=null;
            if (history != null) { // only if it is already versioned
                Version version = history.getVersion(item);
                if(history.isLastVersion(version)){ // only if the user is modifying the last version
                    Version previous = history.getPrevious(version);

                    String idPrevious = getDoiValue(previous.getItem());
                    String suffixIdPrevious=idPrevious.substring(idPrevious.lastIndexOf(SLASH)+1);
                    String suffixIdDoi=idDoi.substring(idDoi.lastIndexOf(SLASH)+1);


                    if(suffixIdPrevious.equals(suffixIdDoi)){   // only if it is not upgraded
                        if(countBitstreams(previous.getItem())!= countBitstreams(item)){ // only if a bitstream was added or removed
                            int versionN = Integer.parseInt(suffixIdPrevious.substring(suffixIdPrevious.lastIndexOf(DOT)+1));

                            String prefix=idDoi.substring(0, idDoi.lastIndexOf(DOT));
                            String newDoi=prefix + DOT + (versionN+1);
                            doi = new DOI(newDoi, itemURL);
                            updateHasPartDataPackage(c, item, doi.toString(), idDoi);
                        }
                    }
                }
            }
            return doi;
        }


        private int countBitstreams(Item item) throws SQLException {
            int numberOfBitsream=0;
            for(Bundle b : item.getBundles())
                for(Bitstream bit : b.getBitstreams())
                    numberOfBitsream++;
            return numberOfBitsream;
        }

        private String updateIdentierPreviousItem(Item item) throws AuthorizeException, SQLException {
            DCValue[] doiVals = item.getMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, Item.ANY);
            String id = doiVals[0].value;
            item.clearMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null);

            id += DOT + "1";
            item.addMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null, id);
            item.update();
            return id;
        }

        private void updateIdentierPreviousDF(Item item) throws AuthorizeException, SQLException {
            DCValue[] doiVals = item.getMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, Item.ANY);
            String id = doiVals[0].value;
            item.clearMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null);

            String prefix = id.substring(0, id.lastIndexOf(SLASH));
            String suffix = id.substring(id.lastIndexOf(SLASH));

            id = prefix + DOT + "1" + suffix + DOT + "1";
            item.addMetadata(identifierMetadata.schema, identifierMetadata.element, identifierMetadata.qualifier, null, id);
            item.update();
        }


        private void updateHasPartDataPackage(Context c, Item item, String idNew, String idOld) throws AuthorizeException, SQLException {            
            Item dataPackage =org.dspace.workflow.DryadWorkflowUtils.getDataPackage(c, item);
            DCValue[] doiVals = dataPackage.getMetadata(identifierMetadata.schema, "relation", "haspart", Item.ANY);

           
            dataPackage.clearMetadata(identifierMetadata.schema, "relation", "haspart", null);

            for(DCValue value : doiVals){
                if(!value.value.equals(idOld))
                    dataPackage.addMetadata(identifierMetadata.schema, "relation", "haspart", null, value.value);
            }
            dataPackage.addMetadata(identifierMetadata.schema, "relation", "haspart", null, idNew);
            dataPackage.update();
        }



        private boolean existsIdDOI(String idDoi) {
            String dbDoiId = lookup(idDoi.toString());

            if (dbDoiId != null && !dbDoiId.equals(""))
                return true;

            return false;
        }


        private DOI getCanonicalDataPackage(DOI doi) {
            String canonicalID = doi.toString().substring(0, doi.toString().lastIndexOf(DOT));
            DOI canonical = new DOI(canonicalID, doi.getTargetURL());
            return canonical;
        }

        private String getCanonicalDataPackage(String doi) {
            // no version present
            if(countDots(doi) <=2) return doi;
            String canonicalID = doi.toString().substring(0, doi.toString().lastIndexOf(DOT));
            return canonicalID;
        }

        private short countDots(String doi){
            short index=0;
            int indexDot=0;
            while( (indexDot=doi.indexOf(DOT))!=-1){
                doi=doi.substring(indexDot+1);
                index++;
            }

            return index;
        }


        /**
         * input doi.toString()=   2rdfer334.3/1.1
         * output doi.toString()=  2rdfer334/1
         */
        private DOI getCanonicalDataFile(DOI doi) {

            String idDP = doi.toString().substring(0, doi.toString().lastIndexOf(SLASH));
            String idDF = doi.toString().substring(doi.toString().lastIndexOf(SLASH) + 1);


            String canonicalDP = idDP.substring(0, idDP.lastIndexOf(DOT));
            String canonicalDF = idDF.substring(0, idDF.lastIndexOf(DOT));


            DOI canonical = new DOI(canonicalDP + SLASH + canonicalDF, doi.getTargetURL());
            return canonical;
        }


        private String getCollection(Context context, Item item) throws SQLException {
            String collectionResult = null;

            if(item.getOwningCollection()!=null)
                return item.getOwningCollection().getHandle();

            // If our item is a workspaceitem it cannot have a collection, so we will need to get our collection from the workspace item
            return getCollectionFromWI(context, item.getID()).getHandle();                            
        }

        private Collection getCollectionFromWI(Context context, int itemId) throws SQLException {

            TableRow row = DatabaseManager.querySingleTable(context, "workspaceitem", "SELECT collection_id FROM workspaceitem WHERE item_id= ?", itemId);
            if (row != null) return Collection.find(context, row.getIntColumn("collection_id"));

            row = DatabaseManager.querySingleTable(context, "workflowitem", "SELECT collection_id FROM workflowitem WHERE item_id= ?", itemId);
            if (row != null) return Collection.find(context, row.getIntColumn("collection_id"));

            throw new RuntimeException("Collection not found for item: " + itemId);

        }

        private URL getTarget(String aDSpaceURL) {
            URL target;
            try {
                target = new URL(aDSpaceURL);
            }
            catch (MalformedURLException details) {
                try {
                    log.debug("Using " + myHostname + " for URL domain name");
                    // If we aren't given a full URL, create one with config value
                    if (aDSpaceURL.startsWith("/")) {
                        target = new URL(myHostname + aDSpaceURL);
                    } else {
                        target = new URL(myHostname + "/handle/" + aDSpaceURL);
                    }
                }
                catch (MalformedURLException moreDetails) {
                    throw new RuntimeException("Passed URL isn't a valid URL: " + aDSpaceURL);
                }
            }
            return target;
        }


        /**
         * Breaks down the DSpace handle-like string (e.g.,
         * http://dev.datadryad.org/handle/12345/dryad.620) into a "12345/dryad.620"
         * part and a "dryad.620" part (in that order).
         *
         * @param aHDL
         * @return
         */
        public String[] stripHandle(String aHDL) {
            int start = aHDL.lastIndexOf(myHdlPrefix + "/") + 1
                    + myHdlPrefix.length();
            String id;

            if (start > myHdlPrefix.length()) {
                id = aHDL.substring(start, aHDL.length());
                return new String[]{myHdlPrefix + "/" + id, id};
            } else {
                return new String[]{myHdlPrefix + "/" + aHDL, aHDL};
            }
        }


        private String buildVar() {
            String bigInt = new BigInteger(mySuffixVarLength * 5, myRandom).toString(32);
            StringBuilder buffer = new StringBuilder(bigInt);
            int charCount = 0;

            while (buffer.length() < mySuffixVarLength) {
                buffer.append('0');
            }

            for (int index = 0; index < buffer.length(); index++) {
                char character = buffer.charAt(index);
                int random;

                if (character == 'a' | character == 'l' | character == 'e'
                        | character == 'i' | character == 'o' | character == 'u') {
                    random = myRandom.nextInt(9);
                    buffer.setCharAt(index, String.valueOf(random).charAt(0));
                    charCount = 0;
                } else if (Character.isLetter(character)) {
                    charCount += 1;

                    if (charCount > 2) {
                        random = myRandom.nextInt(9);
                        buffer.setCharAt(index, String.valueOf(random).charAt(0));
                        charCount = 0;
                    }
                }
            }

            return buffer.toString();
        }


        private org.dspace.versioning.VersionHistory retrieveVersionHistory(Context c, Item item) {
            VersioningService versioningService = new DSpace().getSingletonService(VersioningService.class);
            org.dspace.versioning.VersionHistory history = versioningService.findVersionHistory(c, item.getID());
            return history;
        }

        private org.dspace.versioning.Version getVersion(Context c, Item item) {
            VersioningService versioningService = new DSpace().getSingletonService(VersioningService.class);
            return versioningService.getVersion(c, item);
        }


        private String incrementVar(String aVar) {
            return aVar; // we need to be able to increment too as a second option
        }

        private Item getItem(Context aContext, String aHandle) throws SQLException {
            return (Item) HandleManager.resolveToObject(aContext, aHandle);
        }


    }
}
