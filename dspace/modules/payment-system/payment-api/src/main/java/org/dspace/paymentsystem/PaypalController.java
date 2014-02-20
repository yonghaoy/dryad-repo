package org.dspace.paymentsystem;


import org.dspace.content.*;

import org.dspace.core.Context;

import org.dspace.workflow.WorkflowItem;

import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.RequestMapping;


import javax.servlet.http.HttpServletRequest;


/**
 * Created by IntelliJ IDEA.
 * User: fabio.bolognesi
 * Date: 8/29/11
 * Time: 12:24 PM
 * To change this template use File | Settings | File Templates.
 */

@Controller
public class PaypalController {

    public static final String DSPACE_OBJECT = "dspace.object";
    private static final String RESOURCE = "/resource";
    private static final String METS = "mets";
    private static final String DRI = "DRI";

    private static final int STATUS_OK=200;
    private static final int STATUS_FORBIDDEN=400;

    @RequestMapping("/submit-paypal-checkout")
    public String paypalReturnStep(HttpServletRequest request) {
        Context context =null;
        String type = request.getParameter("TRXTYPE");
        String itemId = request.getParameter("USER1");
        try {
            context = new Context();
            context.turnOffAuthorisationSystem();

            Item item = Item.find(context,Integer.parseInt(itemId));

            // if item is a dataFile retrieve the token from the dataPacakge

            String contextPath = request.getContextPath();
            //RequestDispatcher dispatcher=null;
            if(type.equals("A"))
            {
                WorkspaceItem workspaceItem = WorkspaceItem.findByItemId(context,item.getID());
                String actionUrl = contextPath+"/submit-checkout?workspaceID="+workspaceItem.getID();
                //paypalService.generateUserForm(context,body,actionUrl,knotId,"A",request,item);
                //response.sendRedirect(actionUrl);
                //dispatcher = request.getRequestDispatcher(actionUrl);
                return "forward:"+actionUrl;
            }
            else if(type.equals("S"))
            {
                WorkflowItem workflowItem = WorkflowItem.findByItemId(context,item.getID());
                Collection collection = Collection.find(context,workflowItem.getCollection().getID());
                String actionUrl = contextPath + "/handle/"+collection.getHandle() +"/workflow?workflowID="+workflowItem.getID()+ "&stepID=reAuthorizationPaymentStep&actionID=reAuthorizationPaymentAction";
                //paypalService.generateUserForm(context,body,actionUrl,knotId,"S",request,item);
                //response.sendRedirect(actionUrl);
                //dispatcher = request.getRequestDispatcher(actionUrl);
                return "forward:"+actionUrl;
            }


            // dispatcher.forward(request, response);
        }catch (Exception e)
        {
            String secureToken = request.getParameter("SECURETOKEN");
            String result = request.getParameter("RESULT");
            String message = request.getParameter("RESPMSG");
            String reference = request.getParameter("PNREF");
            String time = request.getParameter("TRANSTIME");
            return "forward:/error";
        }
        //nothing found
        return "forward:/error";
    }
}