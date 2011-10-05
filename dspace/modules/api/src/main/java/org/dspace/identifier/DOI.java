package org.dspace.identifier;

import org.apache.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;

public class DOI {

	transient private Logger LOG = Logger.getLogger(DOI.class);

	private String myURL;
	private String myPrefix;
	private String mySuffix;

	protected DOI() {
		super();
	}

	public DOI(String aDOIString, URL aURL) throws DOIFormatException {
		this(aDOIString);
		setTargetURL(aURL);
	}

	public DOI(String aDOIString) throws DOIFormatException {
		String id = aDOIString.substring(4);
		int index = id.indexOf('/');

		if (!aDOIString.startsWith("doi:"))
			throw new DOIFormatException("DOI strings must start with doi:");

		if (!id.startsWith("10."))
			throw new DOIFormatException("DOI prefixes must start with 10.");

		if (index == -1)
			throw new DOIFormatException("No DOI prefix / suffix separator");

		myPrefix = new String(id.substring(0, index));
		mySuffix = new String(id.substring(index + 1));
	}

	public DOI(String aPrefix, String aSuffix) throws DOIFormatException {
		if (!aPrefix.startsWith("10."))
			throw new DOIFormatException("DOIs must start with '10.'");

		myPrefix = new String(aPrefix);
		mySuffix = new String(aSuffix);
	}

	/**
	 * Outputs a string form of the DOI (<code>doi:10.3456/dryad.12054</code>).
	 * 
	 * @return A String form of the DOI
	 */
	public String toString() {
		return "doi:" + myPrefix.toString() + "/" + mySuffix.toString();
	}

	/**
	 * Outputs the unique ID part of the DOI (<code>10.3456/dryad.12054</code>).
	 * 
	 * @return The raw content (ID) of the DOI
	 */
	public String toID() {
		return myPrefix.toString() + "/" + mySuffix.toString();
	}

	/**
	 * Outputs the URL string that references the DOI resolving service at
	 * doi.org (<code>http://dx.doi.org/10.3456/dryad.12054</code>).
	 * 
	 * @return The URL at which the DOI can be resolved
	 */
	public String toExternalForm() {
		return "http://dx.doi.org/" + myPrefix.toString() + "/"
				+ mySuffix.toString();
	}

	/**
	 * The URL form of the external form returned by
	 * <code>toExternalForm()</code>.
	 * 
	 * @return The URL of the external form of the DOI
	 * @throws java.net.MalformedURLException If the URL isn't a proper URL
	 */
	public URL toURL() throws MalformedURLException {
		return new URL(toExternalForm());
	}

	public void setTargetURL(URL aURL) {
		myURL = new String(aURL.toString());
	}

	public URL getTargetURL() {
		try {
			return new URL(myURL.toString());
		}
		catch (MalformedURLException details) {
			throw new RuntimeException(details); // shouldn't happen
		}
	}

	public String getPrefix() {
		return myPrefix.toString();
	}

	public String getSuffix() {
		return mySuffix.toString();
	}

	/**
	 * If this DOI is a data package DOI and you want to create a related data
	 * file DOI from it, you can use this method to do that. This method makes a
	 * minimal check that it isn't already a data file DOI. If this check fails,
	 * it throws a OperationNotSupportedException
	 * 
	 * @param aInteger
	 * @param aURL
	 * @return
	 */
	public DOI createDataFileDOI(int aInteger, String aURL)
			throws MalformedURLException {
		String suffix = mySuffix.toString();

		if (suffix.contains("/"))
			throw new UnsupportedOperationException(
					"Can't create a data file DOI from a data file DOI");

		suffix = mySuffix.toString() + "/" + aInteger;
		DOI doi = new DOI(myPrefix.toString(), suffix);
		doi.setTargetURL(new URL(aURL));

		return doi;
	}

	/**
	 * Use clones to make sure the transactions are used in explicit put()s and
	 * set()s to the database.
	 */
	public DOI cloneDOI() {
		DOI doi; // non-persisted DOI

		try {
			doi = new DOI(myPrefix.toString(), mySuffix.toString());
			doi.setTargetURL(new URL(myURL.toString()));
		}
		catch (MalformedURLException details) {
			throw new RuntimeException(details);
		}
		catch (DOIFormatException details) {
			throw new RuntimeException(details);
		}

		return doi;
	}

	public boolean equals(Object aObject) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Checking DOIs using equals()");
		}

		if (aObject == null) { return false; }
		if (aObject == this) { return true; }
		if (aObject instanceof String && toString().equals(aObject)) {
			return true;
		}
		if (!(aObject instanceof DOI)) {
			LOG.warn("In equals() instanceof comparison, DOI was "
					+ aObject.getClass().getName());
			return false;
		}

		DOI doi = (DOI) aObject;

		if (LOG.isDebugEnabled()) {
			LOG.debug("Checking DOI prefix | suffix: \"" + myPrefix + "\" = \""
					+ doi.myPrefix + "\" | \"" + mySuffix + "\" = \""
					+ doi.mySuffix + "\"");
		}

		if (myPrefix.toString().equals(doi.myPrefix.toString())
				&& mySuffix.toString().equals(doi.mySuffix.toString())) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(doi.toString() + " is equal to compared DOI");
			}

			return true;
		}
		else {
			if (LOG.isDebugEnabled()) {
				LOG.debug(doi.toString() + " isn't equal to compared DOI: "
						+ myPrefix.toString().equals(doi.myPrefix.toString()) + " | "
						+ mySuffix.toString().equals(doi.mySuffix.toString()));
			}

			return false;
		}
	}

	public int hashCode() {
		int hash = (myPrefix.toString().hashCode() + mySuffix.toString()
				.hashCode()) * 9;

		if (LOG.isDebugEnabled()) {
			LOG.debug("Checking hashCode(): " + hash);
		}

		return hash;
	}
}
