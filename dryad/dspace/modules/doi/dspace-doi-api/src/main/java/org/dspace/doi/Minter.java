package org.dspace.doi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dspace.core.ConfigurationManager;

@SuppressWarnings("deprecation")
public class Minter {

	private static Logger LOG = Logger.getLogger(Minter.class);

	private boolean myDataCiteConnectionIsLive;
	private CDLDataCiteService myDoiService;
	private DOIDatabase myLocalDatabase;
	private String myDataPkgColl;
	private String myDataFileColl;
	private String myHdlPrefix;
	private String myHostname;

	/**
	 * Initialized Minter used when called from script or JUnit test.
	 */
	public Minter() {
		this(null, true);
	}

	public Minter(File aConfig) {
		this(aConfig, false);
	}

	/**
	 * Initialized Minter used when called from servlet.
	 * @param aConfig
	 * @param aBatch
	 */
	public Minter(File aConfig, boolean aBatch) {
		if (aConfig != null) {
			if (aConfig.exists() && aConfig.canRead() && aConfig.isFile()) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Using " + aConfig.getAbsolutePath());
				}

				ConfigurationManager.loadConfig(aConfig.getAbsolutePath());
			}
			else if (!aConfig.exists()) {
				throw new RuntimeException(aConfig.getAbsolutePath()
						+ " doesn't exist");
			}
			else if (!aConfig.canRead()) {
				throw new RuntimeException("Can't read the dspace.cfg file");
			}
			else if (!aConfig.isFile()) {
				throw new RuntimeException(
						"Err, seems like the dspace.cfg isn't a file?");
			}
		}

		// In the case of unit tests, we're not yet configured; rely on a build
		// Running as a script, we should already be configured by this point
		// TODO: document that this will cause problems if no build exists
		// TODO: better yet, have this property as a config'ed test resource
		if (!ConfigurationManager.isConfigured()) {
			System.setProperty("dspace.configuration",
					"/opt/dryad/config/dspace.cfg");
		}

		String doiDirString = ConfigurationManager.getProperty("doi.dir");

		if (doiDirString == null || doiDirString.equals("")) {
			String message = "Failed to find ${doi.dir} in dspace.cfg";
			LOG.fatal(message);
			throw new RuntimeException(message);
		}

		File doiDir = new File(doiDirString);

		if (!doiDir.exists()) {
			if (!doiDir.mkdir()) {
				String message = "Failed to create ${doi.dir}";
				LOG.fatal(message);
				throw new RuntimeException(message);
			}
		}

		if (!doiDir.isDirectory() || !doiDir.canWrite()) {
			String message = "Either $(doi.dir} isn't a dir or it isn't writeable";
			LOG.fatal(message);
			throw new RuntimeException(message);
		}

		String doiUsername = ConfigurationManager.getProperty("doi.username");
		String doiPassword = ConfigurationManager.getProperty("doi.password");
		String length = null;

		myHdlPrefix = ConfigurationManager.getProperty("handle.prefix");
		myDoiService = new CDLDataCiteService(doiUsername, doiPassword);
		myHostname = ConfigurationManager.getProperty("dryad.url");
		myLocalDatabase = DOIDatabase.getInstance();
		myDataCiteConnectionIsLive = ConfigurationManager
				.getBooleanProperty("doi.datacite.connected");
		myDataPkgColl = ConfigurationManager.getProperty("stats.datapkgs.coll");
		myDataFileColl = ConfigurationManager
				.getProperty("stats.datafiles.coll");

		if (myDataPkgColl == null || myDataPkgColl.equals("")) {
			throw new RuntimeException(
					"stats.datapkgs.coll in dspace.cfg not configured");
		}

		if (myDataFileColl == null || myDataFileColl.equals("")) {
			throw new RuntimeException(
					"stats.datafiles.coll in dspace.cfg not configured");
		}

		// TODO some error checking on these dspace.cfg values
		try {
			length = ConfigurationManager.getProperty("doi.suffix.length");
		}
		catch (NumberFormatException details) {
			LOG.warn("dspace.cfg error: " + length + " is not a valid number");
		}
	}

	public DOI register(DOI aDOI) throws IOException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Entering register(DOI) method");
		}

		try {
			URL url = aDOI.toURL();
			HttpURLConnection http = (HttpURLConnection) url.openConnection();
			String target = aDOI.getTargetURL().toString();
			String doi = aDOI.toID();

			http.connect();

			if (http.getResponseCode() == 303) {
				if (!http.getHeaderField("Location").equals(target)) {
					if (myDataCiteConnectionIsLive) {
						String response = myDoiService.updateURL(doi, target);

						LOG.debug("Response from DataCite: " + response);
					}
					else {
						LOG.info("Dryad URL updated: " + aDOI + " = " + target);
					}
				}
				else {
					LOG.debug("Ignored: URL " + target + " already registered");
				}
			}
			else {
				if (myDataCiteConnectionIsLive) {
					String response = myDoiService.registerDOI(doi, target);

					LOG.debug("From DataCite: " + response);
				}
				else {
					LOG.info("Dryad URL registered: " + aDOI + " = " + target);
				}
			}

			http.disconnect();
		}
		catch (MalformedURLException details) {
			throw new RuntimeException(details);
		}

		return aDOI;
	}

	/**
	 * Creates a DOI from the supplied DSpace URL string
	 *
     * @param aDOI
	 * @param aDSpaceURL
	 * @return
	 */
	public DOI mintDOI(String aDOI, String aDSpaceURL) {
		URL target;
		DOI doi=null;

		try {
			target = new URL(aDSpaceURL);
		}
		catch (MalformedURLException details) {
			try {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Using " + myHostname + " for URL domain name");
				}

				// If we aren't given a full URL, create one with config value
				if (aDSpaceURL.startsWith("/")) {
					target = new URL(myHostname + aDSpaceURL);
				}
				else {
					target = new URL(myHostname + "/handle/" + aDSpaceURL);
				}
			}
			catch (MalformedURLException moreDetails) {
				throw new RuntimeException("Passed URL isn't a valid URL: "
						+ aDSpaceURL);
			}
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("Checking to see if " + target.toString()
					+ " is in the DOI database");
		}

        doi = myLocalDatabase.getByDOI(aDOI.toString());
        if(doi==null || !doi.getTargetURL().toString().equals(aDSpaceURL.toString())){
			LOG.debug(aDOI + " wasn't found or it is pointing to a different URL, assigning to: " + target.toString());

		    doi = new DOI(aDOI, target);
            if (myLocalDatabase.put(doi)){
                return doi;
            } else{
                throw new RuntimeException("Should be able to put if db doesn't contain DOI");
            }
        }
        return doi;
	}

	/**
	 * Returns an existing (e.g. known) DOI or null if the supplied DOI string
	 * isn't known.
	 *
	 * @param aDOIString
	 * @return
	 */
	public DOI getKnownDOI(String aDOIString) {
		return myLocalDatabase.getByDOI(aDOIString);
	}


    public Set<DOI> getKnownDOIByURL(String url) {
		return myLocalDatabase.getByURL(url);
	}

    public Set<DOI> getALlKnownDOI() {
		return myLocalDatabase.getALL();
	}


	/**
	 * Breaks down the DSpace URL (e.g.,
	 * http://dev.datadryad.org/handle/12345/dryad.620) into a "12345/dryad.620"
	 * part and a "dryad.620" part.
	 *
	 * @param aDSpaceURL
	 * @return Metadata about item gathered from the URL
	 */
	public URLMetadata getURLMetadata(String aDSpaceURL) {
		int breakPoint = aDSpaceURL.lastIndexOf(myHdlPrefix + "/") + 1;
		int start = breakPoint + myHdlPrefix.length();

		if (start > myHdlPrefix.length()) {
			String id = aDSpaceURL.substring(start, aDSpaceURL.length());
			return new URLMetadata(myHdlPrefix + "/" + id, id);
		}
		else {
			return new URLMetadata(myHdlPrefix + "/" + aDSpaceURL, aDSpaceURL);
		}
	}

	public void dump(OutputStream aOut) throws IOException {
		myLocalDatabase.dump(aOut);
	}

	public int count() throws IOException {
		return myLocalDatabase.size();
	}

	public boolean remove(DOI aDOI) {
		if (myLocalDatabase.contains(aDOI)) {
			return myLocalDatabase.remove(aDOI);
		}
		else {
			return false;
		}
	}

	public void close() {
		myLocalDatabase.close();
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
			return new String[] { myHdlPrefix + "/" + id, id };
		}
		else {
			return new String[] { myHdlPrefix + "/" + aHDL, aHDL };
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// For testing...
		Minter minter = new Minter(new File("/opt/dryad/config/dspace.cfg"));
		// For running from within DSpace script interface...
		// Minter minter = new Minter();

		// testing purposes only
		args = new String[] { "-m",
				"http://datadryad.org/handle/10255/dryad.100" };

		// Do the fake optparse waltz... what does dspace use for this?
		if (args.length > 0) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Initial arguments: " + Arrays.toString(args));
			}

			switch (args[0].charAt(0)) {
			case '-':
				switch (args[0].charAt(1)) {
				case 'h':
					printUsage();
					break;
				case 's':
					if (args.length != 2) {
						printUsage();
					}
					else {
						DOI doi = minter.getKnownDOI(args[1]);

						if (doi != null) {
							System.out.println(doi.toString() + " "
									+ doi.getTargetURL());
						}
						else {
							System.out.println(args[1] + " not found");
						}
					}
					break;
				case 'm':
					if (args.length == 3) {
						if (args[1].startsWith("doi:") && args[2].startsWith("http:")) {
							DOI doi = minter.mintDOI(args[1], args[2]);
							System.out.println("DOI: " + doi.toString());
						}
						else {
							printUsage();
						}
					}
					else {
						printUsage();
					}
					break;
				case 'r':
					if (args.length == 3) {
						if (args[1].startsWith("doi:") && args[2].startsWith("http:")) {
							DOI doi = minter.register(minter.mintDOI(args[1], args[2]));
							System.out.println("DOI: " + doi.toString());
						}
						else {
							printUsage();
						}
					}
					else {
						printUsage();
					}
					break;
				case 'c':
					System.out.println("Total DOIs stored in db: "
							+ minter.count());
					break;
				case 'p':
					if (args.length == 2) {
						FileOutputStream out = new FileOutputStream(args[1]);
						minter.dump(out);
					}
					else {
						System.out.println("DOI Dump:");
						System.out.println();
						minter.dump(System.out);
					}
				}
				break;
			default:
				printUsage();
			}
		}
		else {
			printUsage();
		}
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("  ");
		System.out
				.println("  -h              Help... prints this usage information");
		System.out.println("  -s              Search for a known DOI and return it");
		System.out.println("  -m [DOI] [URL]  Mints a new DOI from");
		System.out.println("  -r [DOI] [URL]  Registers a DOI, minting if necessary");
		System.out
				.println("  -p <FILE>       Prints the DOI database to an output stream");
		System.out
				.println("  -c              Outputs the number of DOIs in the database");
	}

	private class URLMetadata {
		@SuppressWarnings("unused")
		private String myItemHandle;
		@SuppressWarnings("unused")
		private String myItemName;

		private URLMetadata(String aItemHandle, String aItemName) {
			myItemHandle = aItemHandle;
			myItemName = aItemName;
		}
	}
}
