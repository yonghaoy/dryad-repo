package org.dspace.identifier;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.springframework.stereotype.Component;

/**
 * User: kevin (kevin at atmire.com)
 * Date: 21-dec-2010
 * Time: 8:35:17
 * This interface is only used by the identifier services so we can use auto wiring for spring
 */
@Component
public interface DSpaceIdentifierService {

    public String register(Context context, DSpaceObject item) throws IdentifierException;

    public String mint(Context context, DSpaceObject dso) throws IdentifierException;

    public DSpaceObject resolve(Context context, String identifier, String... attributes);

    public void delete(Context context, DSpaceObject dso) throws IdentifierException;
}
