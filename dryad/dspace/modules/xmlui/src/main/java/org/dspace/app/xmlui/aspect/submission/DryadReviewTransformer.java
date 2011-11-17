package org.dspace.app.xmlui.aspect.submission;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.ReferenceSet;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.identifier.IdentifierServiceImpl;
import org.dspace.utils.DSpace;
import org.dspace.workflow.DryadWorkflowUtils;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowRequirementsManager;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: kevin (kevin at atmire.com)
 * Date: 18-aug-2010
 * Time: 11:27:27
 *
 * The user interface for the review step
 */
public class DryadReviewTransformer extends AbstractDSpaceTransformer{

    private static final Logger log = Logger.getLogger(DryadReviewTransformer.class);

    protected static final Message T_showfull =
        message("xmlui.Submission.general.showfull");
    protected static final Message T_showsimple =
            message("xmlui.Submission.general.showsimple");
    protected static final Message T_workflow_head =
        message("xmlui.Submission.general.workflow.head");
    protected static final Message T_workflow_trail =
        message("xmlui.Submission.general.workflow.trail");
    protected static final Message T_dspace_home =
        message("xmlui.general.dspace_home");
    protected static final Message T_workflow_title =
        message("xmlui.Submission.general.workflow.title");
    

    private WorkflowItem wfItem;
    private boolean authorized;

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters parameters) throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, parameters);
        authorized = false;

        Request request = ObjectModelHelper.getRequest(objectModel);

        int wfItemId;
        try{
            if(request.getParameter("wfID") != null){
                wfItemId = Integer.parseInt(request.getParameter("wfID"));
                wfItem = WorkflowItem.find(context, wfItemId);
            }
        }catch (Exception e){
            log.error("Error while setting up DryadReviewTransformer", e);
            //Ignore
        }
        try{
            if(request.getParameter("itemID") != null){
                Item item = Item.find(context, Integer.parseInt(request.getParameter("itemID")));
                wfItem = WorkflowItem.findByItemId(context, item.getID());
            }
        }catch (Exception e){
            log.error("Error while setting up DryadReviewTransformer", e);
        }


        if(wfItem == null){
            return;
        }

        String token = request.getParameter("token");
        if(token != null){
            Item datapackage = wfItem.getItem();
            //Check for a data file
            if(DryadWorkflowUtils.getDataPackage(context, wfItem.getItem()) != null)
            {
                //We have a data file, get our data package so we can check its reviewer key
                datapackage = DryadWorkflowUtils.getDataPackage(context, wfItem.getItem());
            }
            

            DCValue[] reviewerKey = datapackage.getMetadata(WorkflowRequirementsManager.WORKFLOW_SCHEMA, "step", "reviewerKey", Item.ANY);

            if(0 < reviewerKey.length){
                authorized = token.equals(reviewerKey[0].value);
            }

            if(authorized){
                request.getSession().setAttribute("reviewerToken", token);
            }

            //If we have an doi, attempt to retrieve the item by it doi
            //TODO: resolve doi on another way !
            String doi = request.getParameter("doi");
            if(doi != null){
                DSpace dspace = new DSpace();
                IdentifierServiceImpl doiService = dspace.getServiceManager().getServiceByName(IdentifierServiceImpl.class.getName(), IdentifierServiceImpl.class);
                Item item = (Item) doiService.resolve(context, doi);
                //Find the data file with the doi
                if(item != null){
                    try {
                        wfItem = WorkflowItem.findByItemId(context, item.getID());
                    } catch (SQLException e) {
                        log.error("Error while resolving doi, doi: " + doi, e);
                    } catch (AuthorizeException e) {
                        log.error("Error while resolving doi, doi: " + doi, e);
                    }
                }
            }
        }
    }

    @Override
    public void addPageMeta(PageMeta pageMeta) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException {
        super.addPageMeta(pageMeta);

        if(wfItem != null){
            pageMeta.addMetadata("title").addContent(T_workflow_title);
            Collection collection = wfItem.getCollection();

            pageMeta.addTrailLink(contextPath + "/",T_dspace_home);
            HandleUtil.buildHandleTrail(collection,pageMeta,contextPath);
            pageMeta.addTrail().addContent(T_workflow_trail);

            Item dataPackage = DryadWorkflowUtils.getDataPackage(context, wfItem.getItem());
            if(dataPackage != null){
                //We have a data package, indicating that we are viewing a data file
                //Add the review key to the page meta so we can use that
                DCValue[] reviewerKeys = dataPackage.getMetadata(WorkflowRequirementsManager.WORKFLOW_SCHEMA, "step", "reviewerKey", Item.ANY);
                if(0 < reviewerKeys.length)
                    pageMeta.addMetadata("identifier", "reviewerKey").addContent(reviewerKeys[0].value);
            }
        }
    }

    @Override
    public void addBody(Body body) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException {
        if(!authorized){
            throw new AuthorizeException("You are not authorized to review the submission");
        }
        Request request = ObjectModelHelper.getRequest(objectModel);

        // June 2011 - update URL from: submission-review to review
        Division div = body.addInteractiveDivision("main-div", contextPath + "/review", Division.METHOD_POST, "");


        //Add an overview of the item in question
        String showfull = request.getParameter("submit_full_item_info");

        // if the user selected showsimple, remove showfull.
        if (showfull != null && request.getParameter("submit_simple_item_info") != null)
            showfull = null;


        DCValue[] vals = wfItem.getItem().getMetadata("dc.title");

        if (showfull == null)
        {
	        ReferenceSet referenceSet = div.addReferenceSet("narf",ReferenceSet.TYPE_SUMMARY_VIEW);
            if(vals != null && vals[0] !=null)
                referenceSet.setHead(vals[0].value);
            else
                referenceSet.setHead(T_workflow_head);
            referenceSet.addReference(wfItem.getItem());
	        div.addPara().addButton("submit_full_item_info").setValue(T_showfull);
        }
        else
        {
            ReferenceSet referenceSet = div.addReferenceSet("narf", ReferenceSet.TYPE_DETAIL_VIEW);
            if(vals != null && vals[0] !=null)
                referenceSet.setHead(vals[0].value);
            else
                referenceSet.setHead(T_workflow_head);
            referenceSet.addReference(wfItem.getItem());
            div.addPara().addButton("submit_simple_item_info").setValue(T_showsimple);

            div.addHidden("submit_full_item_info").setValue("true");
        }
        div.addHidden("token").setValue(request.getParameter("token"));
        div.addHidden("wfID").setValue(String.valueOf(wfItem.getID()));
    }
}
