/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.submission.submit;

import org.apache.cocoon.components.flow.FlowHelper;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Response;
import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.xmlui.aspect.submission.AbstractStep;
import org.dspace.app.xmlui.aspect.submission.FlowUtils;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Button;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.paymentsystem.*;
import org.dspace.submit.AbstractProcessingStep;
import org.dspace.utils.DSpace;
import org.dspace.workflow.WorkflowItem;
import org.xml.sax.SAXException;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;

/**
 * Provides Return Step from Paypal to present result from processed Paypal transaction
 *
 * @author Mark Diggory, mdiggory at atmire.com
 * @author Fabio Bolognesi, fabio at atmire.com
 * @author Lantian Gai, lantian at atmire.com
 */
public class PaypalReturnStep extends AbstractStep {

    private static final Message T_PayPalVerified = message("xmlui.PaymentSystem.shoppingcart.verified");
    private static final Message T_Finalize = message("xmlui.Submission.submit.CheckoutStep.button.finalize");

        private static final Logger log = Logger.getLogger(PaypalReturnStep.class);
        @Override
        public void addBody(Body body) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException {

            Request request = ObjectModelHelper.getRequest(objectModel);
            Map <String,String> pare=request.getParameters();
            String secureToken = request.getParameter("SECURETOKEN");
            String result = request.getParameter("RESULT");
            String message = request.getParameter("RESPMSG");
            String reference = request.getParameter("PNREF");
            String type = request.getParameter("TRXTYPE");
            String time = request.getParameter("TRANSTIME");
            Map requests = request.getParameters();
            //Date now = new Date(time);
            Date now = new Date();
            PaypalService paypalService = new DSpace().getSingletonService(PaypalService.class);
            PaymentSystemService paymentSystemService = new DSpace().getSingletonService(PaymentSystemService.class);
            HttpServletResponse response = (HttpServletResponse)objectModel.get("httpresponse");
            log.debug("paypal secureToken = " + secureToken);
            log.debug("paypal result = " + result);
            log.debug("paypal message = " + message);
            log.debug("paypal reference = " + reference);
            if(secureToken!=null){
                try{
                    //find the correct shopping cart based on the secrue token
                    ShoppingCart shoppingCart = ShoppingCart.findBySecureToken(context,secureToken);
                    int itemId = shoppingCart.getItem();
                    Item item = Item.find(context,itemId);
                    if(shoppingCart!=null){
                        if("0".equals(result) || "4".equals(result))
                        {
                            //successful transaction
                            shoppingCart.setTransactionId(reference);

                            if(item!=null)
                            {
                                 if(message.equals("Verified")){
                                     //authorization
                                    shoppingCart.setStatus(ShoppingCart.STATUS_VERIFIED);
                                     shoppingCart.setOrderDate(now);
                                 }
				                 else if ("4".equals(result)) {
				                    //authorization, but paypal isn't supporting our zero-dollar transaction
                                    shoppingCart.setStatus(ShoppingCart.STATUS_VERIFIED);
                                     shoppingCart.setOrderDate(now);
				                 }
				                 else
                                 {
                                     shoppingCart.setStatus(ShoppingCart.STATUS_COMPLETED);
                                     shoppingCart.setPaymentDate(now);
                                 }
                                //submitUrl = FlowUtils.processPaypalCheckout(context, request,response,item);

                            }
                            else
                            {
                                shoppingCart.setStatus(ShoppingCart.STATUS_DENIlED);
                            }
                        }
                        else
                        {
                            shoppingCart.setStatus(ShoppingCart.STATUS_DENIlED);
			                log.error("There was an error in PayPal card validation. Code = " + result+message);

                        }
                        shoppingCart.update();

                        String knotId = request.getParameter("USER1");
                        if(type.equals("A"))
                        {
                           WorkspaceItem workspaceItem = WorkspaceItem.findByItemId(context,item.getID());
                           String actionUrl = contextPath+"/submit-checkout?workspaceID="+workspaceItem.getID();
                            paypalService.generateUserForm(context,body,actionUrl,knotId,"A",request,item);
                            //response.sendRedirect(actionUrl);
//                            RequestDispatcher dispatcher = request.getRequestDispatcher(actionUrl);
//                            dispatcher.forward(request, response);
                        }
                        else
                        {
                           WorkflowItem workflowItem = WorkflowItem.findByItemId(context,item.getID());
                           Collection collection = workflowItem.getCollection();
                           String actionUrl = contextPath + "/handle/"+collection.getHandle() +"/workflow?workflowID="+workflowItem.getID()+ "&stepID=reAuthorizationPaymentStep&actionID=reAuthorizationPaymentAction";
                           paypalService.generateUserForm(context,body,actionUrl,knotId,"S",request,item);
                            //response.sendRedirect(actionUrl);
//                            RequestDispatcher dispatcher = request.getRequestDispatcher(actionUrl);
//                            dispatcher.forward(request, response);
                        }
                    }
                    else
                    {
                        //can't find the shopingcart for this secure token
                        addErrorLink(body,"can't find the shopingcart for this secure token:"+secureToken);
                    }

                }catch (Exception e)
                {
                    //TODO: handle the exceptions
                   // System.out.println("errors in generate the payment form");
                    log.error("Exception when entering the checkout step:", e);
                    addErrorLink(body,"errors in generate the payment form:"+e.getMessage());
                }
            }
            else
            {
                //no secure token returned,reload the page to pay again or cotact admin
                addErrorLink(body,"Couldn't find security token, Please go back to the submission page."+message);
            }

        }
        private void addErrorLink(Body body,String message)throws WingException
        {
            Division error = body.addDivision("error");
            error.addPara(message);
            error.addList("return").addItemXref("/submissions","My Submissions");
        }
    }
