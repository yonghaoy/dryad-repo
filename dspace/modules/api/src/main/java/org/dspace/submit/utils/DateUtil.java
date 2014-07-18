package org.dspace.submit.utils;

import java.util.Calendar;

public class DateUtil {

		private long StartTime;
		private long EndTime;
		
	    public void setStartTime(long start_long){
             
               StartTime = start_long; 
        }
        public void setEndTime(long end_long){
               EndTime = end_long;
        }
		public boolean isThisPeriod(long now){
				if(now < EndTime && now >= StartTime){
						return true;
				}
				else
						return false;
		}
}
