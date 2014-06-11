
package org.dspace.submit.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


import org.apache.log4j.Logger;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.workflow.DryadWorkflowUtils;
import org.dspace.workflow.WorkflowItem;

//new package
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.LogManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

import java.sql.SQLException;
import java.util.List;

public class InRe{
		public static void main(String[] args) throws Exception{
				Context myContext = new Context();
				count_deposit_publications(myContext,"sina");
		        myContext.complete();
		}
		public static int count_deposit_publications(Context myContext, String journal_name) throws SQLException{
				int count_deposit_publication = 0;
				TableRowIterator rows = DatabaseManager.queryTable(myContext, "shoppingcart", "SELECT * FROM shoppingcart WHERE journal = ' "+ journal_name + "'");
				try{
						List<TableRow> propertyRows = rows.toList();
						System.out.println(propertyRows.size());
						for (int i = 0; i < propertyRows.size(); i++)
						{
								TableRow row = (TableRow) propertyRows.get(i);
								System.out.println(row.getIntColumn("cart_id"));
								System.out.println(row.getDateColumn("payment_date"));
								System.out.println(row.getStringColumn("status"));
						}

						return count_deposit_publication;		

				}
				finally{
						if (rows != null)
								{
										rows.close();
								}
				}
		}



}
