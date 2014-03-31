/*
 */
package org.dspace.doi;

import java.util.Set;
import org.dspace.core.ConfigurationManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.utils.DSpace;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test cases for DOI Database in Postgres.  All DOIs created use
 * PGDOIDatabase.internalTestingPrefix for prefix
 * @author Dan Leehr <dan.leehr@nescent.org>
 */
public class PGDOIDatabaseTest {
    private static PGDOIDatabase myPGDOIDatabase;
    private static String myRandomSuffix, myRandomSuffixModified;
    private static String url1, url2;
    @BeforeClass
    public static void setupBeforeClass() {
        myPGDOIDatabase = PGDOIDatabase.getInstance();
        int randomInt = (int) (Math.random() * 10000);
        myRandomSuffix = String.format("test-suffix-%d", randomInt);
        myRandomSuffixModified = String.format("test-suffix-%d-modified", randomInt);
        url1 = "http://test-suffix.doi.org/1/" + myRandomSuffix;
        url2 = "http://test-suffix.doi.org/2/" + myRandomSuffix;
        // delete DOIs created by this class
        int removed = myPGDOIDatabase.removeTestDOIs();
        System.out.println("Removed " + removed + " test DOIs before running tests");
    }

    @AfterClass
    public static void teardownAfterClass() {
        // delete DOIs created by this class
        int removed = myPGDOIDatabase.removeTestDOIs();
        System.out.println("Removed " + removed + " test DOIs after running tests");
        myPGDOIDatabase.close();
    }

    @Test
    public void testSet() {
        // Verify a DOI can be set
        DOI aDOI = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffix, url1);
        DOI setDOI = myPGDOIDatabase.set(aDOI);
        assert aDOI.equals(setDOI);

        // Verify the DOI we set can be retrieved
        DOI getDOI = myPGDOIDatabase.getByDOI(aDOI.toString());
        assert aDOI.equals(getDOI);

        //Verify set also works to change the target of the DOI
        // change the target URL of the DOI
        DOI otherDOI = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffix, url2);
        // Update the DOI
        boolean put = myPGDOIDatabase.put(otherDOI);
        getDOI = myPGDOIDatabase.getByDOI(aDOI.toString());
        // The DOI internal identifiers should not be equal
        // even though they have the same prefix/suffix
        assert aDOI.getInternalIdentifier().equals(getDOI.getInternalIdentifier()) == false;
        assert otherDOI.equals(getDOI);
    }

    @Test
    public void testPutContainsRemove() {
        DOI aDOI = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffixModified,url1);
        assert myPGDOIDatabase.put(aDOI);
        // make sure put was successful
        assert myPGDOIDatabase.contains(aDOI);
        assert myPGDOIDatabase.remove(aDOI);
    }

    @Test
    public void testGetByDOI() {
        DOI aDOI = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffixModified,url1);
        assert myPGDOIDatabase.put(aDOI);
        // doi:10.5061/dryad.xxxxx
        String doiKey = "doi:" + PGDOIDatabase.internalTestingPrefix + '/' + myRandomSuffixModified;
        DOI byKey = myPGDOIDatabase.getByDOI(doiKey);
        assert byKey.equals(aDOI);
    }

    @Test
    public void testGetByURL() {
        DOI aDOI = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffixModified,url1);
        assert myPGDOIDatabase.put(aDOI);
        // doi:10.5061/dryad.xxxxx
        Set<DOI> DOIsbyURL = myPGDOIDatabase.getByURL(url1);
        assert DOIsbyURL.contains(aDOI);
    }

    @Test
    public void testGetALL() {
        DOI aDOI1 = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffix,url1);
        DOI aDOI2 = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffixModified,url2);
        assert myPGDOIDatabase.put(aDOI1);
        assert myPGDOIDatabase.put(aDOI2);
        Set<DOI> allDOIs = myPGDOIDatabase.getALL();
        assert allDOIs.isEmpty() == false;
        assert allDOIs.contains(aDOI1);
        assert allDOIs.contains(aDOI2);
        assert allDOIs.size() >= 2;
    }

    @Test
    public void testSize() {
        DOI aDOI = new DOI(PGDOIDatabase.internalTestingPrefix, myRandomSuffixModified,url1);
        assert myPGDOIDatabase.put(aDOI);
        int size = myPGDOIDatabase.size();
        assert size > 0;
    }

    @Test
    public void testConcurrency() {
        assert false;
    }
}
