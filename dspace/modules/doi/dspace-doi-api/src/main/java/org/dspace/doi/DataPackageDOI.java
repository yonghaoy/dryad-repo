package org.dspace.doi;

import org.garret.perst.L2List;

/**
 * Not used.
 * 
 * @author Kevin S. Clarke <ksclarke@gmail.com>
 *
 */
public class DataPackageDOI extends DOI {

	private L2List myDataFileDOIs;
	
	@SuppressWarnings("unused")
	private DataPackageDOI() {
		super();
		myDataFileDOIs = new L2List();
	}
	
	public DataPackageDOI(DOI aDOI) {
		super(aDOI.toString(), aDOI.getTargetURL());
	}
	
	public DataPackageDOI(String aDOIString) throws DOIFormatException {
		super(aDOIString);
	}
	
	public boolean link(DOI aDOI) {
		return myDataFileDOIs.add(aDOI);
	}
	
	public int countLinks() {
		return myDataFileDOIs.size();
	}
	
	public boolean isLinkedTo(DOI aDOI) {
		return myDataFileDOIs.contains(aDOI);
	}
}
