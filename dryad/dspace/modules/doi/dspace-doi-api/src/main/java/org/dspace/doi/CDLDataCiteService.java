package org.dspace.doi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

public class CDLDataCiteService {

	private static final Logger LOGGER = Logger
			.getLogger(CDLDataCiteService.class);

	private static final String BASEURL = "https://n2t.net/ezid";

	private String myUsername;
	private String myPassword;

	public CDLDataCiteService(final String aUsername, final String aPassword) {
		myUsername = aUsername;
		myPassword = aPassword;
	}

	/**
	 * 
	 * @param aDOI A DOI in the form <code>10.5061/dryad.1731</code>
	 * @param aURL A URL in the form
	 *        <code>http://datadryad.org/handle/10255/dryad.1731</code>
	 * @return A response message from the remote service
	 * @throws IOException If there was trouble connection and communicating to
	 *         the remote service
	 */
	public String registerDOI(String aDOI, String aURL) throws IOException {
		HttpsURLConnection http = makeConnection(aDOI, "PUT");
		List<String[]> list = new ArrayList<String[]>();

		list.add(new String[] { "_target", aURL });

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Adding _target to metadata for reg: " + aURL);
		}

		// add other types of metadata in the future

		return sendMetadata(list, http);
	}

	/**
	 * 
	 * @param aDOI A DOI in the form <code>10.5061/dryad.1731</code>
	 * @param aURL A URL in the form
	 *        <code>http://datadryad.org/handle/10255/dryad.1731</code>
	 * @return A response message from the remote service
	 * @throws IOException If there was trouble connection and communicating to
	 *         the remote service
	 */
	public String updateURL(String aDOI, String aURL) throws IOException {
		HttpsURLConnection http = makeConnection(aDOI, "POST");
		List<String[]> list = new ArrayList<String[]>();

		list.add(new String[] { "_target", aURL });

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Adding _target to metadata for update: " + aURL);
		}

		// add other types of metadata in the future

		return sendMetadata(list, http);
	}

	private String sendMetadata(List<String[]> aMetadataList,
			HttpsURLConnection aHTTP) throws IOException {
		OutputStream outStream = aHTTP.getOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(outStream);
		char[] response = new char[0];

		aHTTP.connect();

		try {
			for (String[] pair : aMetadataList) {
				writer.write(pair[0] + ": " + pair[1] + "\n");
			}

			writer.close();

			// Seems CDL is returning "Bad request" with a 200 response code(?)
			if (aHTTP.getResponseCode() == 200) {
				InputStream inStream = aHTTP.getInputStream();
				InputStreamReader reader = new InputStreamReader(inStream);

				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Content-Length from CDL: "
							+ aHTTP.getContentLength());
				}

				response = new char[aHTTP.getContentLength()];
				reader.read(response, 0, aHTTP.getContentLength());

				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Content: " + new String(response));
				}

				if (new String(response).equalsIgnoreCase("Bad request.")) {
					LOGGER.error(new String(response));
					
					throw new IOException("Received 'Bad Request' from CDL");
				}
			}
			else {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Received unsuccessful response from CDL: "
							+ aHTTP.getResponseCode() + " "
							+ aHTTP.getResponseMessage());
				}
			}
		}
		catch (IOException details) {
			LOGGER.error("CDLDataCiteService IOException", details);
			response = "Action failed, check the ERROR log".toCharArray();
		}

		aHTTP.disconnect();

		if (aHTTP.getResponseCode() == 200) {
			return aHTTP.getResponseMessage() + " " + new String(response);
		}
		else {
			return aHTTP.getResponseMessage();
		}
	}

	private HttpsURLConnection makeConnection(String aDOI, String aMethod)
			throws IOException {
		URL url = new URL(BASEURL + "/id/doi%3A" + aDOI);
		HttpsURLConnection http = (HttpsURLConnection) url.openConnection();

		http.setRequestProperty("Content-Type", "text/plain");
		http.setRequestProperty("Accept", "text/plain");
		http.setDoOutput(true);
		http.setDoInput(true);
		http.setRequestMethod(aMethod);

		Base64 encoder = new Base64();
		String userpassword = myUsername + ":" + myPassword;
		String auth = new String(encoder.encode(userpassword.getBytes()));
		http.setRequestProperty("Authorization", "Basic " + auth);

		return http;
	}

	/**
	 * Have to test this on dev since it's also IP restricted.
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		String usage = "Usage: class username password doi target register|update";
		CDLDataCiteService service;

		if (args.length == 5) {
			String username = args[0];
			String password = args[1];
			String doiID = args[2];
			String target = args[3];
			String action = args[4];

			service = new CDLDataCiteService(username, password);

			if (action.equals("register")) {
				System.out.println(service.registerDOI(doiID, target));
			}
			else if (action.equals("update")) {
				System.out.println(service.updateURL(doiID, target));
			}
			else {
				System.out.println(usage);
			}
		}
		else {
			System.out.println(usage);
		}
	}

	/*
	 * For example (from /opt/dryad):
	 * 
	 * `java -cp
	 * config:lib/doi-service-0.0.2.jar:lib/commons-codec-1.3.jar:lib/log4j-1.2.14.jar
	 * org.dspace.doi.CDLDataCiteService username password 10.5061/dryad.1786/2
	 * http://datadryad.org/handle/10255/dryad.1799 register`
	 * 
	 * TODO: make a real dspace script?
	 * 
	 * Note: this puts username/password in shell history (see wiki docs for
	 * more on this)
	 */
}
