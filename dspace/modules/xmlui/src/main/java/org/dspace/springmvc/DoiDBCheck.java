package org.dspace.springmvc;

import org.dspace.doi.DOI;
import org.dspace.doi.Minter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@Controller
@RequestMapping("/doidb-check")
public class DoiDBCheck {

    private static final Logger log = LoggerFactory.getLogger(DoiDBCheck.class);
    private static final String DUMMY_DOI_STRING = "doi:10.5072/FK2/10.5061/dryad.AAAAAABBB";

    @RequestMapping("/doidb-check")
    public void checkDoiDb(HttpServletRequest request, HttpServletResponse response) {
        PrintWriter writer = null;
        try {
            // Attempts a simple write operation to see if the DOI file is usable
            writer = response.getWriter();
            Minter myMinter = new Minter();
            // Minter does not instantiate a DOIDatabase until calling getKnownDOI()
            DOI doi = myMinter.getKnownDOI(DUMMY_DOI_STRING);
            DOI dummyDoi = new DOI(DUMMY_DOI_STRING, DOI.Type.TOMBSTONE);
            // Mint a DOI then remove it
            myMinter.mintDOI(dummyDoi); // If DOI is already in DB this does not write
            myMinter.remove(dummyDoi); // This writes
            writer.println("OK");
        } catch (Exception ex) {
            writer.println("Exception: " + ex);
        }
    }


}
