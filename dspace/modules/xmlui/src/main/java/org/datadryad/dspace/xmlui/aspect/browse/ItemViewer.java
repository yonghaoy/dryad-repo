/**
 * $Id: ItemViewer.java 4707 2010-01-19 09:17:47Z mdiggory $
 * $URL: https://scm.dspace.org/svn/repo/modules/dspace-discovery/trunk/block/src/main/java/org/dspace/app/xmlui/aspect/discovery/ItemViewer.java $
 * *************************************************************************
 * Copyright (c) 2002-2009, DuraSpace.  All rights reserved
 * Licensed under the DuraSpace License.
 *
 * A copy of the DuraSpace License has been included in this
 * distribution and is available at: http://scm.dspace.org/svn/repo/licenses/LICENSE.txt
 */

package org.datadryad.dspace.xmlui.aspect.browse;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.util.HashUtil;
import org.apache.excalibur.source.SourceValidity;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.DSpaceValidity;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.ReferenceSet;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Para;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.DisseminationCrosswalk;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.LogManager;
import org.dspace.core.PluginManager;
import org.dspace.handle.HandleManager;
import org.dspace.identifier.DOIIdentifierService;
import org.dspace.identifier.DSpaceIdentifierService;
import org.dspace.utils.DSpace;
import org.dspace.versioning.VersionHistory;
import org.dspace.versioning.VersioningService;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.xml.sax.SAXException;

/**
 * Display a single item.
 * 
 * @author Scott Phillips
 * @author Kevin S. Clarke
 */
public class ItemViewer extends AbstractDSpaceTransformer implements
		CacheableProcessingComponent {
	private static final Logger log = Logger.getLogger(ItemViewer.class);

	/** Language strings */
	private static final Message T_dspace_home = message("xmlui.general.dspace_home");

	private static final Message T_trail = message("xmlui.ArtifactBrowser.ItemViewer.trail");

	private static final Message T_show_simple = message("xmlui.ArtifactBrowser.ItemViewer.show_simple");

	private static final Message T_show_full = message("xmlui.ArtifactBrowser.ItemViewer.show_full");

	private static final Message T_head_parent_collections = message("xmlui.ArtifactBrowser.ItemViewer.head_parent_collections");

    private static final Message T_withdrawn = message("xmlui.DryadItemSummary.withdrawn");
    private static final Message T_not_current_version = message("xmlui.DryadItemSummary.notCurrentVersion");
    private static final Message T_most_current_version = message("xmlui.DryadItemSummary.mostCurrentVersion");

    private static final Message T_head_has_part = message("xmlui.ArtifactBrowser.ItemViewer.head_hasPart");
	private static final Message T_head_is_part_of = message("xmlui.ArtifactBrowser.ItemViewer.head_isPartOf");


    private List<Item>dataFiles=new ArrayList<Item>();


	/** Cached validity object */
	private SourceValidity validity = null;

	/** XHTML crosswalk instance */
	private DisseminationCrosswalk xHTMLHeadCrosswalk = null;

	/**
	 * Generate the unique caching key. This key must be unique inside the space
	 * of this component.
	 */
	public Serializable getKey() {
		try {
			DSpaceObject dso = HandleUtil.obtainHandle(objectModel);

			if (dso == null) return "0"; // no item, something is wrong.

			return HashUtil.hash(dso.getHandle() + "full:"
					+ showFullItem(objectModel));
		}
		catch (SQLException sqle) {
			// Ignore all errors and just return that the component is not
			// cachable.
			return "0";
		}
	}

	/**
	 * Generate the cache validity object.
	 * 
	 * The validity object will include the item being viewed, along with all
	 * bundles & bitstreams.
	 */
    public SourceValidity getValidity() {
        DSpaceObject dso = null;

        if (this.validity == null) {
            try {
                dso = HandleUtil.obtainHandle(objectModel);

                DSpaceValidity validity = new DSpaceValidity();
                if (dso instanceof Item) {
                    Item item = (Item) dso;
                    retrieveDataFiles(item);

                    for(Item i : dataFiles){
                        validity.add(i);
                    }
                }
                validity.add(dso);

                this.validity = validity.complete();
            } catch (Exception e) {
               log.error("Exception: getValidity()", e);
            }

            // add log message that we are viewing the item
            // done here, as the serialization may not occur if the cache is
            // valid
            log.info(LogManager.getHeader(context, "view_item", "handle="
                    + (dso == null ? "" : dso.getHandle())));
        }
        return this.validity;
	}



    /**
	 * Add the item's title and trail links to the page's metadata.
	 */
	public void addPageMeta(PageMeta pageMeta) throws SAXException,
			WingException, UIException, SQLException, IOException,
			AuthorizeException {
		DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
		if (!(dso instanceof Item)) return;
		Item item = (Item) dso;

		// Set the page title
		String title = getItemTitle(item);

		if (title != null) pageMeta.addMetadata("title").addContent(title);
		else pageMeta.addMetadata("title").addContent(item.getHandle());

		pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
		HandleUtil.buildHandleTrail(item, pageMeta, contextPath);
		pageMeta.addTrail().addContent(T_trail);

		// Find out whether our theme should be localized
		String localize = ConfigurationManager.getProperty("dryad.localize");

		if (localize != null && localize.equals("true")) {
			pageMeta.addMetadata("dryad", "localize").addContent("true");
		}

		// Data file metadata included on data package items (integrated view)
		for (DCValue metadata : item.getMetadata("dc.relation.haspart")) {
			int skip = 0;
			String id;

			if (metadata.value.startsWith("http://hdl.")) {
				skip = 22;
			}
			else if (metadata.value.indexOf("/handle/") != -1) {
				skip = metadata.value.indexOf("/handle/") + 8;
			}
			// else DOI, stick with skip == 0

			id = metadata.value.substring(skip); // skip host name

			if (id.startsWith("doi:") || id.startsWith("http://dx.doi.org/")) {
				if (id.startsWith("http://dx.doi.org/")) {
					id = id.substring("http://dx.doi.org/".length());

					// service with resolve with or without the "doi:" prepended
					if (!id.startsWith("doi:")) {
						id = "doi:" + id;
					}
				}

				DOIIdentifierService doiService = new DOIIdentifierService();
				Item file = (Item) doiService.resolve(context, id, new String[]{});

				if (file != null) {
					String fileTitle = getItemTitle(file);

					if (fileTitle != null) {
						pageMeta.addMetadata("dryad", "fileTitle").addContent(metadata.value + "|" + fileTitle);
					}
					else {
						pageMeta.addMetadata("dryad", "fileTitle").addContent(metadata.value);
					}
				}
				else {
					log.warn("Didn't find a DOI from internal db for: " + id);
				}
			}
			else {
				Item file = (Item) HandleManager.resolveToObject(context, id);
				String fileTitle = getItemTitle(file);

				if (fileTitle != null) {
					pageMeta.addMetadata("dryad", "fileTitle").addContent(metadata.value + "|" + fileTitle);
				}
				else {
					pageMeta.addMetadata("dryad", "fileTitle").addContent(metadata.value);
				}
			}
		}

		// Data package metadata included on data file items
		for (DCValue metadata : item.getMetadata("dc.relation.ispartof")) {
			int skip = 0;

			if (metadata.value.startsWith("http://hdl.")) {
				skip = 22;
			}
			else if (metadata.value.indexOf("/handle/") != -1) {
				skip = metadata.value.indexOf("/handle/") + 8;
			}
			else {
				// if doi, leave as is and we'll process differently below
			}

			String id = metadata.value.substring(skip); // skip host name
			Item pkg;

			if (id.startsWith("doi:")) {
				DOIIdentifierService doiService = new DOIIdentifierService();
				pkg = (Item) doiService.resolve(context, id, new String[]{});
			}
			else {
				pkg = (Item) HandleManager.resolveToObject(context, id);
			}

			StringBuilder buffer = new StringBuilder();
			boolean identifierSet = false;
			DCValue[] values;
			String date;

			if (pkg != null) {
				String pkgTitle = getItemTitle(pkg).trim();
				String author;

				for (DCValue pkgMeta : pkg
						.getMetadata("dc.identifier.citation")) {
					pageMeta.addMetadata("citation", "article").addContent(
							pkgMeta.value);
				}

				buffer.append(parseName(pkg
						.getMetadata("dc.contributor.author")));
				buffer.append(parseName(pkg.getMetadata("dc.creator")));
				buffer.append(parseName(pkg.getMetadata("dc.contributor")));

				author = buffer.toString().trim();
				author = author.endsWith(",") ? author.substring(0, author
						.length() - 1) : author;

				pageMeta.addMetadata("authors", "package").addContent(
						author + " ");
				pageMeta.addMetadata("title", "package").addContent(
						pkgTitle.endsWith(".") ? pkgTitle + " " : pkgTitle
								+ ". ");

				if ((values = pkg.getMetadata("dc.date.issued")).length > 0) {
					pageMeta.addMetadata("dateIssued", "package").addContent(
							"(" + values[0].value.substring(0, 4) + ")");
				}

				if ((values = pkg.getMetadata("dc.relation.isreferencedby")).length != 0) {
					pageMeta.addMetadata("identifier", "article").addContent(
							values[0].value);
				}

				if ((values = pkg.getMetadata("prism.publicationName")).length != 0) {
					pageMeta.addMetadata("publicationName").addContent(
							values[0].value);
				}

				if ((values = pkg.getMetadata("dc.identifier")).length != 0) {
					for (DCValue value : values) {
						if (value.value.startsWith("doi:")) {
							pageMeta.addMetadata("identifier", "package")
									.addContent(value.value);
						}
					}
				}
				else if ((values = pkg.getMetadata("dc.identifier.uri")).length != 0) {
					for (DCValue value : values) {
						if (value.value.startsWith("doi:")) {
							pageMeta.addMetadata("identifier", "package")
									.addContent(value.value);
							identifierSet = true;
						}
					}

					if (!identifierSet) {
						for (DCValue value : values) {
							if (value.value.startsWith("http://dx.doi.org/")) {
								pageMeta.addMetadata("identifier", "package")
										.addContent(value.value.substring(18));
								identifierSet = true;
							}
						}
					}

					if (!identifierSet) {
						for (DCValue value : values) {
							if (value.value.startsWith("hdl:")) {
								pageMeta.addMetadata("identifier", "package")
										.addContent(value.value);
								identifierSet = true;
							}
						}
					}

					if (!identifierSet) {
						for (DCValue value : values) {
							if (value.value
									.startsWith("http://hdl.handle.net/")) {
								pageMeta.addMetadata("identifier", "package")
										.addContent(value.value.substring(22));
							}
						}
					}
				}
			}
		}
		/**
		 * TODO: We can use the trail here to reference parent Article and/or
		 * original search links
		 */

		// Metadata for <head> element
		if (xHTMLHeadCrosswalk == null) {
			xHTMLHeadCrosswalk = (DisseminationCrosswalk) PluginManager
					.getNamedPlugin(DisseminationCrosswalk.class,
							"XHTML_HEAD_ITEM");
		}

		// Produce <meta> elements for header from crosswalk
		try {
			List l = xHTMLHeadCrosswalk.disseminateList(item);
			StringWriter sw = new StringWriter();

			XMLOutputter xmlo = new XMLOutputter();
			for (int i = 0; i < l.size(); i++) {
				Element e = (Element) l.get(i);
				// FIXME: we unset the Namespace so it's not printed.
				// This is fairly yucky, but means the same crosswalk should
				// work for Manakin as well as the JSP-based UI.
				e.setNamespace(null);
				xmlo.output(e, sw);
			}
			pageMeta.addMetadata("xhtml_head_item").addContent(sw.toString());
		}
		catch (CrosswalkException ce) {
			// TODO: Is this the right exception class?
			throw new WingException(ce);
		}
	}

	/**
	 * Display a single item
	 */
	public void addBody(Body body) throws SAXException, WingException,
			UIException, SQLException, IOException, AuthorizeException {

		DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
		if (!(dso instanceof Item)) return;
		Item item = (Item) dso;

		// Build the item viewer division.
		Division division = body.addDivision("item-view", "primary");
		String title = getItemTitle(item);
		if (title != null) division.setHead(title);
		else division.setHead(item.getHandle());



        // Adding message withdrwan or not most current version
        addWarningMessage(item, division);


		Para showfullPara = division.addPara(null,
				"item-view-toggle item-view-toggle-top");

		if (showFullItem(objectModel)) {
			String link = contextPath + "/handle/" + item.getHandle();
			showfullPara.addXref(link).addContent(T_show_simple);
		}
		else {
			String link = contextPath + "/handle/" + item.getHandle()
					+ "?show=full";
			showfullPara.addXref(link).addContent(T_show_full);
		}

		ReferenceSet referenceSet;
		if (showFullItem(objectModel)) {
			referenceSet = division.addReferenceSet("collection-viewer",ReferenceSet.TYPE_DETAIL_VIEW);
		}
		else {
			referenceSet = division.addReferenceSet("collection-viewer",ReferenceSet.TYPE_SUMMARY_VIEW);
		}

		// Reference the actual Item referenceSet.addReference(item);

		/*
		 * reference any isPartOf items to create listing...
		 */
        DOIIdentifierService dis = new DSpace().getSingletonService(DOIIdentifierService.class);
        org.dspace.app.xmlui.wing.element.Reference itemRef = referenceSet.addReference(item);

        if (item.getMetadata("dc.relation.haspart").length > 0){
            ReferenceSet hasParts;
            hasParts = itemRef.addReferenceSet("embeddedView", null, "hasPart");
            hasParts.setHead(T_head_has_part);

            if(dataFiles.size() == 0) retrieveDataFiles(item);

            for(Item obj : dataFiles){

                log.warn("ItemViewer: addReference() - obj ==>> " + obj);
                log.warn("ItemViewer: addReference() - obj ==>> " + obj.getHandle());
                log.warn("ItemViewer: addReference() - obj ==>> " + obj.getClass().getName());

                hasParts.addReference(obj);
            }
        }

        ReferenceSet appearsInclude = itemRef.addReferenceSet(ReferenceSet.TYPE_DETAIL_LIST, null, "hierarchy");
		appearsInclude.setHead(T_head_parent_collections);

		//Reference all collections the item appears in.
		for (Collection collection : item.getCollections()) {
			appearsInclude.addReference(collection);
		}
	}


     private void retrieveDataFiles(Item item) throws SQLException {
        DOIIdentifierService dis = new DSpace().getSingletonService(DOIIdentifierService.class);

        if (item.getMetadata("dc.relation.haspart").length > 0) {
            dataFiles=new ArrayList<Item>();
            for (DCValue value : item.getMetadata("dc.relation.haspart")) {

                log.warn("ItemViewer: try to resolve - value ==>> " + value.value);

                DSpaceObject obj = dis.resolve(context, value.value);
                dataFiles.add((Item) obj);
            }
        }
    }

    private void addWarningMessage(Item item, Division division) throws WingException {
        Message message=null;
        if(item.isWithdrawn()){
            Division div = division.addDivision("notice", "notice");
            Para p = div.addPara();
            p.addContent(T_withdrawn);
        }
        else{
             VersioningService versioningService = new DSpace().getSingletonService(VersioningService.class);
             VersionHistory history = versioningService.findVersionHistory(context, item.getID());
             if(history !=null && !history.isLastVersion(history.getVersion(item))){
                Division div = division.addDivision("notice", "notice");
                Para p = div.addPara();
                p.addContent(T_not_current_version);

                String link = contextPath + "/handle/" + history.getLatestVersion().getItem().getHandle();
                p.addXref(link, T_most_current_version);
             }
        }
    }

    /**
	 * Determine if the full item should be referenced or just a summary.
	 */
	public static boolean showFullItem(Map objectModel) {
		Request request = ObjectModelHelper.getRequest(objectModel);
		String show = request.getParameter("show");

		if (show != null && show.length() > 0) return true;
		return false;
	}

	/**
	 * Obtain the item's title.
	 */
	public static String getItemTitle(Item item) {
		DCValue[] titles = item.getDC("title", Item.ANY, Item.ANY);

		String title;
		if (titles != null && titles.length > 0) title = titles[0].value;
		else title = null;
		return title;
	}

	/**
	 * Recycle
	 */
	public void recycle() {
		this.validity = null;
		super.recycle();
	}

	private String parseName(DCValue[] aMetadata) {
		StringBuilder buffer = new StringBuilder();
		int position = 0;

		for (DCValue metadata : aMetadata) {

			if (metadata.value.indexOf(",") != -1) {
				String[] parts = metadata.value.split(",");
				
				if (parts.length > 1) {
					StringTokenizer tokenizer = new StringTokenizer(parts[1], ". ");

					buffer.append(parts[0]).append(" ");

					while (tokenizer.hasMoreTokens()) {
						buffer.append(tokenizer.nextToken().charAt(0));
					}
				}
			}
			else {
				// now the minority case (as we clean up the data)
				String[] parts = metadata.value.split("\\s+|\\.");
				String author = parts[parts.length - 1].replace("\\s+|\\.", "");
				char ch;

				buffer.append(author).append(" ");

				for (int index = 0; index < parts.length - 1; index++) {
					if (parts[index].length() > 0) {
						ch = parts[index].replace("\\s+|\\.", "").charAt(0);
						buffer.append(ch);
					}
				}
			}

			if (++position < aMetadata.length) {
				if (aMetadata.length > 2) {
					buffer.append(", ");
				}
				else {
					buffer.append(" and ");
				}
			}
		}

		return buffer.length() > 0 ? buffer.toString() : "";
	}
}