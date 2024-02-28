/*
 ***************************************************************
 *                                                             *
 *                           NOTICE                            *
 *                                                             *
 *   THIS SOFTWARE IS THE PROPERTY OF AND CONTAINS             *
 *   CONFIDENTIAL INFORMATION OF INFOR AND/OR ITS AFFILIATES   *
 *   OR SUBSIDIARIES AND SHALL NOT BE DISCLOSED WITHOUT PRIOR  *
 *   WRITTEN PERMISSION. LICENSED CUSTOMERS MAY COPY AND       *
 *   ADAPT THIS SOFTWARE FOR THEIR OWN USE IN ACCORDANCE WITH  *
 *   THE TERMS OF THEIR SOFTWARE LICENSE AGREEMENT.            *
 *   ALL OTHER RIGHTS RESERVED.                                *
 *                                                             *
 *   (c) COPYRIGHT 2020 INFOR.  ALL RIGHTS RESERVED.           *
 *   THE WORD AND DESIGN MARKS SET FORTH HEREIN ARE            *
 *   TRADEMARKS AND/OR REGISTERED TRADEMARKS OF INFOR          *
 *   AND/OR ITS AFFILIATES AND SUBSIDIARIES. ALL RIGHTS        *
 *   RESERVED.  ALL OTHER TRADEMARKS LISTED HEREIN ARE         *
 *   THE PROPERTY OF THEIR RESPECTIVE OWNERS.                  *
 *                                                             *
 ***************************************************************
 */

 import groovy.lang.Closure
 
 import java.time.LocalDate;
 import java.time.LocalDateTime;
 import java.time.format.DateTimeFormatter;
 import java.time.ZoneId;
 import groovy.json.JsonSlurper;
 import java.math.BigDecimal;
 import java.math.RoundingMode;
 import java.text.DecimalFormat;


/*
 *Modification area - M3
 *Nbr               Date      User id     Description
 *ABF100            20231112  RDRIESSEN   Mods BF0100- Write/Update EXTSCL records as a basis for Scales integration
 *
 */

/*
* - Write the record to EXTSCL
*/
public class Add extends ExtendM3Transaction {
  private final MIAPI mi;
  private final DatabaseAPI database;
  private final MICallerAPI miCaller;
  private final LoggerAPI logger;
  private final ProgramAPI program;
  private final IonAPI ion;
  
  //Input fields
  private String panr;
  private String msg1;
  private String msg2;
  private String stat;
  private String oStat;
  private String grwe;
  private int currentDate;
  private int currentTime;
  private int xxCONO;
 
 /*
  * Add Scales dashboard extension table row
 */
  public Add(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program, IonAPI ion) {
    this.mi = mi;
    this.database = database;
  	this.miCaller = miCaller;
  	this.logger = logger;
  	this.program = program;
	  this.ion = ion;
	  
  }
  
  public void main() {
    
  	panr = mi.inData.get("PANR") == null ? '' : mi.inData.get("PANR").trim();
  	if (panr == "?") {
  	  panr = "";
  	}
  	
  	msg1 = mi.inData.get("MSG1") == null ? '' : mi.inData.get("MSG1").trim();
  	if (msg1 == "?") {
  	  msg1 = "";
  	}
  	
  	msg2 = mi.inData.get("MSG2") == null ? '' : mi.inData.get("MSG2").trim();
  	if (msg2 == "?") {
  	  msg2 = "";
  	}
  	
  	grwe = mi.inData.get("GRWE") == null ? '' : mi.inData.get("GRWE").trim();
  	if (grwe == "?") {
  	  grwe = "";
  	}
  	
  	stat = mi.inData.get("STAT") == null ? '' : mi.inData.get("STAT").trim();
  	if (stat == "?") {
  	  stat = "";
  	} 
  	
  	xxCONO = (Integer)program.LDAZD.CONO;

    // Validate input fields  	
		if (panr.isEmpty()) {
      mi.error("Container No must be entered");
      return;
    }
    
    
    DBAction query01 = database.table("EXTSCL").index("00").selection("EXPANR", "EXMSG1", "EXMSG2", "EXSTAT").build();
    DBContainer container01 = query01.getContainer();
    container01.set("EXCONO", xxCONO);
    container01.set("EXPANR", panr);
    if (query01.read(container01)) {
      oStat  = container01.get("EXSTAT").toString();
    } 
    
    
    //only delete and add record when status EXTSCL not equal to 90
    if(oStat != '90') {
      DBAction query02 = database.table("EXTSCL").index("00").build();
      DBContainer container02 = query02.getContainer();
      container02.set("EXCONO", xxCONO);
      container02.set("EXPANR", panr);
      if (!query02.readLock(container02, deleteCallBack)) {}
        writeEXTSCL(panr, msg1, msg2, stat, grwe);
        mi.outData.put("MSG1", "updated")
      } else { mi.outData.put("MSG1", "completed")  };
  }

  /*
  * Write Scales Results extension table EXTSCL
  *
  */
  void writeEXTSCL(String panr, String msg1, String msg2, String stat, String grwe) {
	  
    ZoneId zid = ZoneId.of("Australia/Sydney"); 
    currentDate = LocalDate.now(zid).format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger();
    currentTime = Integer.valueOf(LocalDateTime.now(zid).format(DateTimeFormatter.ofPattern("HHmmss")));
  	
	  DBAction actionEXTSCL = database.table("EXTSCL").build();
  	DBContainer EXTSCL = actionEXTSCL.getContainer();
  	EXTSCL.set("EXCONO", xxCONO);
  	EXTSCL.set("EXPANR", panr);
  	EXTSCL.set("EXMSG1", msg1);
  	EXTSCL.set("EXMSG2", msg2);
  	EXTSCL.set("EXSTAT", stat);
  	EXTSCL.set("EXGRWE", grwe.toDouble());
  	EXTSCL.set("EXRGDT", currentDate);
  	EXTSCL.set("EXRGTM", currentTime);
  	EXTSCL.set("EXLMDT", currentDate);
  	EXTSCL.set("EXCHNO", 0);
  	EXTSCL.set("EXCHID", program.getUser());
  	actionEXTSCL.insert(EXTSCL, recordExists);
	}
  /*
   * recordExists - return record already exists error message to the MI
   *
  */
  Closure recordExists = {
	  mi.error("Record already exists");
  }
  
  /*
   * deleteCallback - Delete record if PANR exists and status not equal to 90
   *
  */
  Closure<?> deleteCallBack = { LockedResult lockedResult ->
    lockedResult.delete();
  }
}