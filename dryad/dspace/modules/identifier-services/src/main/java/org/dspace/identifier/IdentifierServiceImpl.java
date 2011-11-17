package org.dspace.identifier;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.utils.DSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: kevin (kevin at atmire.com)
 * Date: 14-dec-2010
 * Time: 14:28:28
 *
 * The main service class used to reserver, register and resolve identifiers
 */
@Component
public class IdentifierServiceImpl {

    @Autowired
    private List<DSpaceIdentifierService> providers;

    @Autowired
    private HandleIdentifierService handleService;


    public void setHandleService(HandleIdentifierService handleService)
   {
       this.handleService = handleService;
   }

   public void setProviders(List<DSpaceIdentifierService> providers)
   {
       this.providers = providers;
   }

    public List<DSpaceIdentifierService> getProviders() {
        return providers;
    }

    public HandleIdentifierService getHandleService() {
        return handleService;
    }

    /**
     * Reserves identifiers for the item
     * @param context dspace context
     * @param item dspace item
     */
    public void reserve(Context context, Item item) throws AuthorizeException, SQLException, IdentifierException {
        if(handleService != null){
            handleService.mint(context, item);
        }
        
        // Next resolve all other services
        for (DSpaceIdentifierService service : providers){
            if(!(service instanceof HandleIdentifierService))
                service.mint(context, item);
        }
        //Update our item
        item.update();
    }

    public String register(Context context, Item item) throws AuthorizeException, SQLException, IdentifierException {
        if(handleService != null){
            handleService.register(context, item);
        }

        //We need to commit our context because one of the providers might require the handle created above
        // Next resolve all other services
        for (DSpaceIdentifierService service : providers){
            if(!(service instanceof HandleIdentifierService))
                service.register(context, item);
        }
        //Update our item
        item.update();

        return null;
    }

    public DSpaceObject resolve(Context context, String identifier){
        for (DSpaceIdentifierService service : providers) {
            DSpaceObject result = service.resolve(context, identifier);
            if (result != null)
                return result;
        }

        return null;
    }


    public void delete(Context context, Item item) throws AuthorizeException, SQLException, IdentifierException {
       for (DSpaceIdentifierService service : providers) {
            service.delete(context, item);
        }
    }

}
