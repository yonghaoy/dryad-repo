package org.datadryad.dspace.xmlui;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

import junit.framework.Assert;
import junit.framework.TestCase;

public class RSSFeedsTest extends TestCase {

	@Test
	public void testRSSFeeds() {
		try {
			URL url = new URL("http://datadryad.org/feed/rss_2.0/10255/3");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			if (conn.getResponseCode() != 200) {
				Assert.fail("Didn't receive a successful response back from Dryad \"Recently Published\"");
			}
			
			conn.disconnect();
			
			url = new URL("http://blog.datadryad.org/feed/");
			conn = (HttpURLConnection) url.openConnection();
			
			if (conn.getResponseCode() != 200) {
				Assert.fail("Didn't receive a successful response back from Dryad Blog");
			}
			
			conn.disconnect();
		}
		catch (MalformedURLException details) {
			Assert.fail(details.getMessage());
		}
		catch (IOException details) {
			Assert.fail(details.getMessage());
		}
	}
	
	public static void main(String... args) {
		new RSSFeedsTest().testRSSFeeds();
	}
}
