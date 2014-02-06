/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.paymentsystem;

import edu.harvard.hul.ois.mets.helper.DateTime;
import org.apache.cocoon.environment.Request;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.xmlui.aspect.submission.FlowUtils;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.content.Item;
import org.dspace.core.*;
import org.dspace.eperson.EPerson;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.submit.AbstractProcessingStep;
import org.dspace.utils.DSpace;
import org.dspace.workflow.WorkflowItem;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Random;

import org.dspace.app.xmlui.wing.Message;

/**
 *  Paypal Service for interacting with Payflow Pro API
 *
 * @author Mark Diggory, mdiggory at atmire.com
 * @author Fabio Bolognesi, fabio at atmire.com
 * @author Lantian Gai, lantian at atmire.com
 */
public class PaypalImpl implements PaypalService{

    protected Logger log = Logger.getLogger(PaypalImpl.class);

    public String getSecureTokenId(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSSSSSSSSS");
       return sdf.format(new Date());


        //return DigestUtils.md5Hex(new Date().toString()); //"9a9ea8208de1413abc3d60c86cb1f4c5";
    }

    //generate a secure token from paypal
    public String generateSecureToken(ShoppingCart shoppingCart,String secureTokenId,String itemID, String type){
        String secureToken=null;
        String requestUrl = ConfigurationManager.getProperty("payment-system","paypal.payflow.link");

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            PostMethod get = new PostMethod(requestUrl);

            get.addParameter("SECURETOKENID",secureTokenId);
            get.addParameter("CREATESECURETOKEN","Y");
            get.addParameter("MODE",ConfigurationManager.getProperty("payment-system","paypal.mode"));
            get.addParameter("PARTNER",ConfigurationManager.getProperty("payment-system","paypal.partner"));
            get.addParameter("SILENTTRAN",ConfigurationManager.getProperty("payment-system","paypal.slienttran"));

            get.addParameter("VENDOR",ConfigurationManager.getProperty("payment-system","paypal.vendor"));
            get.addParameter("USER",ConfigurationManager.getProperty("payment-system","paypal.user"));
            get.addParameter("PWD", ConfigurationManager.getProperty("payment-system","paypal.pwd"));
            //get.addParameter("RETURNURL", URLEncoder.encode("http://us.atmire.com:8080/submit-paypal-checkout"));
            if(ConfigurationManager.getProperty("payment-system","paypal.returnurl").length()>0)
            get.addParameter("RETURNURL", ConfigurationManager.getProperty("payment-system","paypal.returnurl"));
            get.addParameter("TENDER", "C");
            get.addParameter("TRXTYPE", type);
            if(type.equals("S")){
                get.addParameter("AMT", Double.toString(shoppingCart.getTotal()));
            }
            else
            {
                get.addParameter("AMT", "0.00");
            }
            //TODO:add currency from shopping cart
            get.addParameter("CURRENCY", shoppingCart.getCurrency());
	    log.debug("paypal request URL " + get);
            switch (new HttpClient().executeMethod(get)) {
                case 200:
                case 201:
                case 202:
                    String string = get.getResponseBodyAsString();
                    String[] results = string.split("&");
                    for(String temp:results)
                    {
                        String[] result = temp.split("=");
                        if(result[0].contains("RESULT")&&!result[1].equals("0"))
                        {
                            //failed to get a secure token
                            log.error("Failed to get a secure token from paypal:"+string);
                            log.error("Failed to get a secure token from paypal:"+get);
                            break;
                        }
                        if(result[0].equals("SECURETOKEN"))
                        {
                            secureToken=result[1];
                        }
                    }


                    break;
                default:
                    log.error("get paypal secure token error");
            }

            get.releaseConnection();
        }
        catch (Exception e) {
            log.error("get paypal secure token error:",e);
            return null;
        }

        return secureToken;
    }
    //charge the credit card stored as a reference transaction
    public boolean submitReferenceTransaction(Context c,WorkflowItem wfi,HttpServletRequest request){

        try{
            PaymentSystemService paymentSystemService = new DSpace().getSingletonService(PaymentSystemService.class);
            ShoppingCart shoppingCart = paymentSystemService.getShoppingCartByItemId(c,wfi.getItem().getID());
            if(shoppingCart.getStatus().equals(ShoppingCart.STATUS_COMPLETED)){
                //this shopping cart has already been charged
                return true;
            }
            Voucher voucher = Voucher.findById(c,shoppingCart.getVoucher());

	    // check whether we're using the special voucher that simulates "payment failed"
            if(voucher!=null&&ConfigurationManager.getProperty("payment-system","paypal.failed.voucher")!=null)
            {
                String failedVoucher = ConfigurationManager.getProperty("payment-system","paypal.failed.voucher");
                 if(voucher.getCode().equals(failedVoucher)||voucher.getStatus().equals(Voucher.STATUS_USED))
                 {
                     log.debug("problem: 'payment failed' voucher has been used, rejecting payment");
                     sendPaymentErrorEmail(c, wfi, shoppingCart, "problem: voucher has been used, rejecting payment");
                     return false;
                 }
            }

            if(shoppingCart.getTotal()==0)
            {
                log.debug("shopping cart total is 0, not charging card");
                sendPaymentWaivedEmail(c, wfi, shoppingCart);
                //if the total is 0 , don't charge
                return true;
            }
            else
            {
                log.debug("charging card");
                return chargeCard(c, wfi, request,shoppingCart);
            }

        }catch (Exception e)
        {
            sendPaymentErrorEmail(c, wfi, null, "exception when submitting reference transaction " + e.getMessage());
            log.error("exception when submiting reference transaction ", e);
        }
        return false;
    }

    @Override
    public boolean chargeCard(Context c, WorkflowItem wfi, HttpServletRequest request, ShoppingCart shoppingCart) {
        //this method should get the reference code and submit it to paypal to do the actural charge process

        if(shoppingCart.getTransactionId()==null){
            log.debug("transaction id absent, cannot change card");
            return false;
        }
        if(shoppingCart.getStatus().equals(ShoppingCart.STATUS_COMPLETED))
        {

            //all ready changed
            return true;
        }

        String requestUrl = ConfigurationManager.getProperty("payment-system","paypal.payflow.link");
        try {



            PostMethod get = new PostMethod(requestUrl);

            //setup the reference transaction
            get.addParameter("TENDER", "C");
            get.addParameter("TRXTYPE", "S");
            get.addParameter("PWD", ConfigurationManager.getProperty("payment-system","paypal.pwd"));
            get.addParameter("AMT", Double.toString(shoppingCart.getTotal()));
            get.addParameter("VENDOR",ConfigurationManager.getProperty("payment-system","paypal.vendor"));
            get.addParameter("PARTNER",ConfigurationManager.getProperty("payment-system","paypal.partner"));
            get.addParameter("USER", ConfigurationManager.getProperty("payment-system","paypal.user"));
            get.addParameter("ORIGID", shoppingCart.getTransactionId());

            //TODO:add currency from shopping cart
            get.addParameter("CURRENCY", shoppingCart.getCurrency());
	    log.debug("paypal sale transaction url " + get);
            switch (new HttpClient().executeMethod(get)) {
                case 200:
                case 201:
                case 202:
                    String string = get.getResponseBodyAsString();
                    String[] results = string.split("&");
                    for(String temp:results)
                    {
                        String[] result = temp.split("=");
                        //TODO: ignore the error from paypal server, add the error check after figure out the correct way to process the credit card info
                        if(result[0].contains("RESULT")&&result[1].equals("0"))
                        {
                            //successfull
                            shoppingCart.setStatus(ShoppingCart.STATUS_COMPLETED);
                            Date date= new Date();
                            shoppingCart.setPaymentDate(date);
                            for(String s:results)
                            {
                                String[] strings = s.split("=");
                                if(strings[0].contains("PNREF"))
                                {
                                    shoppingCart.setTransactionId(strings[1]);
                                    break;
                                }
                            }

                            shoppingCart.update();
                            sendPaymentApprovedEmail(c, wfi, shoppingCart);
                            return true;
                        }

                    }
                    break;
                default:
                    String result = "Paypal Reference Transaction Failure: "
                            + get.getStatusCode() +  ": " + get.getResponseBodyAsString();
                    log.error(result);
                    sendPaymentRejectedEmail(c, wfi, shoppingCart);
                    return false;
            }

            get.releaseConnection();
        }
        catch (Exception e) {
            log.error("error when submit paypal reference transaction: " + e.getMessage(), e);
            sendPaymentErrorEmail(c, wfi, null, "exception when submit reference transaction: " + e.getMessage());
            return false;
        }
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void generateVoucherForm(Division form,String voucherCode,String actionURL,String knotId) throws WingException{

        List list=form.addList("voucher-list");
        list.addLabel("Voucher Code");
        list.addItem().addText("voucher").setValue(voucherCode);
        list.addItem().addButton("submit-voucher").setValue("Apply");

    }

    public void generateNoCostForm( Division actionsDiv,ShoppingCart shoppingCart, org.dspace.content.Item item,PaymentSystemConfigurationManager manager,PaymentSystemService paymentSystemService) throws WingException, SQLException {
        //Lastly add the finalize submission button


        if(shoppingCart.getStatus().equals(ShoppingCart.STATUS_VERIFIED))
        {
            actionsDiv.addPara("data-label", "bold").addContent("Your payment information has been verified.");
        }
        if(shoppingCart.getStatus().equals(ShoppingCart.STATUS_COMPLETED))
        {
            actionsDiv.addPara("data-label", "bold").addContent("Your card has been charged.");
        }
        else if(shoppingCart.getTotal()==0)
        {
           actionsDiv.addPara("data-label", "bold").addContent("Your total due is 0.00.");
        }
        else if(!shoppingCart.getCurrency().equals("USD"))
        {
            actionsDiv.addPara("data-label", "bold").addContent("Dryad's payment processing system currently only supports transactions in US dollars. We expect to enable transactions in other currencies within a few days. If you wish to complete your transaction in US dollars, please change the currency setting above. Otherwise, please complete your submission without entering payment information. We will contact you for payment details before your data is published.");
        }
        else
        {
            actionsDiv.addPara("data-label", "bold").addContent("You are not being charged until your submission has been approved by curator.");
        }
    }


    public void addButtons(Division mainDiv, boolean showSkipButton) throws WingException {
        List buttons = mainDiv.addList("paypal-form-buttons");
        if(showSkipButton)  {
            Button skipButton = buttons.addItem().addButton("skip_payment");
            skipButton.setValue("Finalize submission");
        }
        Button cancleButton = buttons.addItem().addButton(AbstractProcessingStep.CANCEL_BUTTON);
        cancleButton.setValue("Cancel");

    }

    //this methord should genearte a secure token from paypal and then generate a user crsedit card form
    public void generateUserForm(Context context,Body body,String actionURL,String knotId,String type,Request request, Item item) throws WingException, SQLException{
        PaymentSystemConfigurationManager manager = new PaymentSystemConfigurationManager();
        PaymentSystemService payementSystemService = new DSpace().getSingletonService(PaymentSystemService.class);
        PaypalService paypalService = new DSpace().getSingletonService(PaypalService.class);
        Map<String,String> messages = null;
        Division mainDiv = body.addInteractiveDivision("submit-completed-dataset", actionURL, Division.METHOD_POST, "primary submission");
        String ecountError = request.getParameter("ecountError");
        String voucherError = request.getParameter("voucherError");
        String countryError = request.getParameter("countryError");
        String currencyError = request.getParameter("currencyError");
        String paypalError = request.getParameter("RESPMSG");
        boolean showSkipButton=false;
        try{
            ShoppingCart shoppingCart = payementSystemService.getShoppingCartByItemId(context,item.getID());

            //check all the changes to shopping cart
            VoucherValidationService voucherValidationService = new DSpace().getSingletonService(VoucherValidationService.class);
            String voucherCode = "";
            if(request.getParameter("submit-voucher")!=null||request.getParameter("voucher")!=null)
            {    //user is using the voucher code
                voucherCode = request.getParameter("voucher");
                if(voucherCode!=null&&voucherCode.length()>0){
                    if(!voucherValidationService.voucherUsed(context,voucherCode)) {
                        Voucher voucher = Voucher.findByCode(context,voucherCode);
                        shoppingCart.setVoucher(voucher.getID());
                    }
                    else
                    {
                        voucherError = "The voucher code is not valid:can't find the voucher code or the voucher code has been used";
                    }
                }
                else
                {
                    shoppingCart.setVoucher(null);

                }

            }
            if(request.getParameter("country")!=null)
            {    //user is using the voucher code

                String newCountry = request.getParameter("country");
                shoppingCart.setCountry(newCountry);

            }
            if(request.getParameter("currency")!=null)
            {    //user is using the voucher code
                String newCurrency = request.getParameter("currency");

                shoppingCart.setCurrency(newCurrency);
            }
            if(voucherError!=null)
            messages.put("voucher",voucherError);
            if(countryError!=null)
            messages.put("countryError",countryError);
            if(currencyError!=null)
            messages.put("currencyError",currencyError);
            payementSystemService.updateTotal(context,shoppingCart,null);
            //generate the shopping cart and insert it into main page , disable the shopping cart in option section
            List shoppingCartlist = mainDiv.addList("shopping-cart");
            shoppingCartlist.addItem().addHidden("hideShoppingCart");
            payementSystemService.generateShoppingCart(context,shoppingCartlist, shoppingCart, manager, "", messages);

            if(shoppingCart.getTotal()==0||shoppingCart.getStatus().equals(ShoppingCart.STATUS_COMPLETED)||!shoppingCart.getCurrency().equals("USD"))
            {
                //already paid, no need to pay again
                showSkipButton=true;
                generateNoCostForm(mainDiv, shoppingCart,item, manager, payementSystemService);
            }
            else if(shoppingCart.getStatus().equals(ShoppingCart.STATUS_VERIFIED)&&type.equals("A"))
            {
                //already verified, no need to do it again
                showSkipButton=true;
                generateNoCostForm(mainDiv, shoppingCart,item, manager, payementSystemService);
            }
            else
            {


//                if(errorMessage!=null&&errorMessage.length()>0) {
//                    mainDiv.addPara("voucher-error","voucher-error").addHighlight("bold").addContent(errorMessage);
//
//                }

//                Voucher voucher1 = Voucher.findById(context,shoppingCart.getVoucher());
//                if(voucher1!=null){
//                    generateVoucherForm(mainDiv,voucher1.getCode(),actionURL,knotId);
//                }
//                else if(voucherCode!=null&&voucherCode.length()>0){
//                    generateVoucherForm(mainDiv,voucherCode,actionURL,knotId);
//                }
//                else{
//                    generateVoucherForm(mainDiv,null,actionURL,knotId);
//                }
                if(paypalError!=null&&paypalError.length()>0)
                {
                    body.addDivision("error").addPara(paypalError);
                }
                generateUserCreditCardForm(context,body,shoppingCart,actionURL,knotId,type);

            }


        }catch (Exception e)
        {
            //TODO: handle the exceptions
            if(type.equals("A")){
                showSkipButton=true;
                mainDiv.addPara("Errors in generate chechout form, please contact system administrator or skip the payment right now and continue to submit the item to curator.");
            }
            else
            {
                showSkipButton=false;
                mainDiv.addPara("Errors in generate chechout form, please contact system administrator.");
            }

            log.error("Exception when entering the checkout step:", e);
        }


        mainDiv.addHidden("submission-continue").setValue(knotId);
        mainDiv.addPara().addContent("NOTE : Proceed only if your submission is finalized. After submitting, a Dryad curator will review your submission. After this review, your data will be archived in Dryad, and your payment will be processed.");
        addButtons(mainDiv,showSkipButton);

    }


    //this methord should genearte a secure token from paypal and then generate a user credit card form
    public void generateUserCreditCardForm(Context context,Body body,ShoppingCart shoppingCart,String actionURL,String knotId,String type) throws WingException, SQLException{

        //generate the secure token from paypal

        String secureToken = null;
        String secureTokenId = getSecureTokenId();
        secureToken = generateSecureToken(shoppingCart, secureTokenId,Integer.toString(shoppingCart.getItem()),type);

        if(secureToken!=null){
            shoppingCart.setSecureToken(secureToken);
            shoppingCart.update();
            Division form = body.addInteractiveDivision("paymentForm", ConfigurationManager.getProperty("payment-system","paypal.form.link"), Division.METHOD_POST,"creditcard");
            form.addPara("Payflow transparent credit card processing - basic demo");

            List formBody = form.addList("paypal-form", List.TYPE_FORM, "paypal");
            org.dspace.app.xmlui.wing.element.Item paypalInfo = formBody.addItem("paypalInfo","paypalInfo");
            paypalInfo.addHidden("SECURETOKENID").setValue(secureTokenId);
            paypalInfo.addHidden("SECURETOKEN").setValue(secureToken);


            paypalInfo.addHidden("VERBOSITY").setValue("HIGH");
            paypalInfo.addHidden("MODE").setValue(ConfigurationManager.getProperty("payment-system", "paypal.mode"));
            paypalInfo.addHidden("TENDER").setValue("C");
            //change the type to be charge imediatly instead of a reference payment
            paypalInfo.addHidden("TRXTYPE").setValue(type);

            String currencyString = shoppingCart.getCurrency();
            paypalInfo.addHidden("CURRENCY").setValue(currencyString);
            formBody.addLabel("Currency:"+currencyString);
            if(type.equals("A"))
                paypalInfo.addHidden("AMT").setValue("0.0");
            else
            paypalInfo.addHidden("AMT").setValue(Double.toString(shoppingCart.getTotal()));
            formBody.addLabel("Total Amount");
            formBody.addItem().addContent(Double.toString(shoppingCart.getTotal()));
            formBody.addLabel("Credit Card Info");
            formBody.addLabel("Credit Card Number");
            formBody.addItem().addText("ACCT").setValue("");
            formBody.addLabel("Experiation Date");
            formBody.addItem().addText("EXPDATE").setValue("");
            formBody.addLabel("CVV2 Number");
            formBody.addItem().addText("CVV2").setValue("");
            formBody.addLabel("Billing First Name");
            formBody.addItem().addText("BILLTOFIRSTNAME").setValue("");
            formBody.addLabel("Billing Last Name");
            formBody.addItem().addText("BILLTOLASTNAME").setValue("");
            formBody.addLabel("Billing Address line 1");
            formBody.addItem().addText("BILLTOSTREET").setValue("");
            formBody.addLabel("Billing Address line 2 (optional)");
            formBody.addItem().addText("BILLTOSTREET2").setValue("");
            formBody.addLabel("Billing City");
            formBody.addItem().addText("BILLTOCITY").setValue("");
            formBody.addLabel("Billing State");
            formBody.addItem().addText("BILLTOSTATE").setValue("");
            formBody.addLabel("Billing Zip");
            formBody.addItem().addText("BILLTOZIP").setValue("");
            formBody.addLabel("Billing Country");
            formBody.addItem().addText("BILLTOCOUNTRY").setValue("");

            EPerson ePerson = EPerson.find(context, shoppingCart.getDepositor());
            String email="";
            if(ePerson!=null){
                email = ePerson.getEmail();
            }else
            {
                email = "";
            }
            formBody.addItem().addText("BILLTOEMAIL").setValue(email);
            formBody.addLabel("Comment");
            formBody.addItem().addText("COMMENT1").setValue(knotId);
            //add this id to make sure the workflow resume
            formBody.addItem().addHidden("submission-continue").setValue(knotId);

            formBody.addItem().addContent("NOTE : Proceed only if your submission is finalized. After submitting, a Dryad curator will review your submission. After this review, your data will be archived in Dryad, and your payment will be processed.");
            Button finishButton = formBody.addItem().addButton(AbstractProcessingStep.NEXT_BUTTON);
            finishButton.setValue("Pay");

        }
        else
        {
            Division form = body.addDivision("form").addInteractiveDivision("paymentForm", actionURL, Division.METHOD_POST);
            form.addPara("Payflow transparent credit card processing - error");

            List formBody = form.addList("paypal-form", List.TYPE_FORM, "paypal");
            formBody.addItem().addContent("NOTE : Proceed only if your submission is finalized. After submitting, a Dryad curator will review your submission. After this review, your data will be archived in Dryad, and your payment will be processed.");

        }


    }

    private void sendPaymentApprovedEmail(Context c, WorkflowItem wfi, ShoppingCart shoppingCart) {

        try {

            Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(c.getCurrentLocale(), "payment_approved"));
            email.addRecipient(wfi.getSubmitter().getEmail());
            email.addRecipient(ConfigurationManager.getProperty("payment-system", "dryad.paymentsystem.alert.recipient"));

            email.addArgument(
                    wfi.getItem().getName()
            );

            email.addArgument(
                    wfi.getSubmitter().getFullName() + " ("  +
                            wfi.getSubmitter().getEmail() + ")");

            if(shoppingCart != null)
            {
                /** add details of shopping cart */
                PaymentSystemService paymentSystemService = new DSpace().getSingletonService(PaymentSystemService.class);
                email.addArgument(paymentSystemService.printShoppingCart(c, shoppingCart));
            }

            email.send();

        } catch (Exception e) {
            log.error(LogManager.getHeader(c, "Error sending payment approved submission email", "WorkflowItemId: " + wfi.getID()), e);
        }

    }

    private void sendPaymentWaivedEmail(Context c, WorkflowItem wfi, ShoppingCart shoppingCart) {

        try {

            Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(c.getCurrentLocale(), "payment_waived"));
            email.addRecipient(wfi.getSubmitter().getEmail());
            email.addRecipient(ConfigurationManager.getProperty("payment-system", "dryad.paymentsystem.alert.recipient"));

            email.addArgument(
                    wfi.getItem().getName()
            );

            email.addArgument(
                    wfi.getSubmitter().getFullName() + " ("  +
                            wfi.getSubmitter().getEmail() + ")");
            if(shoppingCart != null)
            {
                /** add details of shopping cart */
                PaymentSystemService paymentSystemService = new DSpace().getSingletonService(PaymentSystemService.class);
                email.addArgument(paymentSystemService.printShoppingCart(c, shoppingCart));
            }

            email.send();

        } catch (Exception e) {
            log.error(LogManager.getHeader(c, "Error sending payment approved submission email", "WorkflowItemId: " + wfi.getID()), e);
        }

    }

    private void sendPaymentErrorEmail(Context c, WorkflowItem wfi, ShoppingCart shoppingCart, String error) {

        try {

            Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(c.getCurrentLocale(), "payment_error"));
            // only send result of shopping cart errors to administrators
            email.addRecipient(ConfigurationManager.getProperty("payment-system", "dryad.paymentsystem.alert.recipient"));

            email.addArgument(
                    wfi.getItem().getName()
            );

            email.addArgument(
                    wfi.getSubmitter().getFullName() + " ("  +
                            wfi.getSubmitter().getEmail() + ")");

            email.addArgument(error);

            if(shoppingCart != null)
            {
                /** add details of shopping cart */
                PaymentSystemService paymentSystemService = new DSpace().getSingletonService(PaymentSystemService.class);
                email.addArgument(paymentSystemService.printShoppingCart(c, shoppingCart));
            }

            email.send();

        } catch (Exception e) {
            log.error(LogManager.getHeader(c, "Error sending payment rejected submission email", "WorkflowItemId: " + wfi.getID()), e);
        }

    }

    private void sendPaymentRejectedEmail(Context c, WorkflowItem wfi, ShoppingCart shoppingCart) {

        try {

            Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(c.getCurrentLocale(), "payment_rejected"));
            // temporarily only send result of shopping cart errors to administrators
            email.addRecipient(wfi.getSubmitter().getEmail());
            email.addRecipient(ConfigurationManager.getProperty("payment-system", "dryad.paymentsystem.alert.recipient"));

            email.addArgument(
                    wfi.getItem().getName()
            );

            email.addArgument(
                    wfi.getSubmitter().getFullName() + " ("  +
                            wfi.getSubmitter().getEmail() + ")");

            if(shoppingCart != null)
            {
                /** add details of shopping cart */
                PaymentSystemService paymentSystemService = new DSpace().getSingletonService(PaymentSystemService.class);
                email.addArgument(paymentSystemService.printShoppingCart(c, shoppingCart));
            }

            email.send();

        } catch (Exception e) {
            log.error(LogManager.getHeader(c, "Error sending payment rejected submission email", "WorkflowItemId: " + wfi.getID()), e);
        }

    }
}
