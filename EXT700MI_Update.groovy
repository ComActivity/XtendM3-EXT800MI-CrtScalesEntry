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
  public class Update extends ExtendM3Transaction {
    
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
  private String grwe;
  private int currentDate;
  private int currentTime;
  
  private int xxCONO;
  
  public Update(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program, IonAPI ion) {
    this.mi = mi;
    this.database = database;
    this.miCaller = miCaller;
    this.logger = logger;
    this.program = program;
    this.ion = ion;
   
  }
  
  /* Update records in external tables based on what is passed in
  */
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
    stat = mi.inData.get("STAT") == null ? '' : mi.inData.get("STAT").trim();
    if (stat == "?") {
      stat = "";
    }
    
    grwe = mi.inData.get("GRWE") == null ? '' : mi.inData.get("GRWE").trim();
    if (grwe == "?") {
      grwe = "";
    }
    
    xxCONO = (Integer)program.LDAZD.CONO;
  
    if (panr.isEmpty()) {
      mi.error("Container number must be entered");
      return;
    }
  
    DBAction query = database.table("EXTSCL").index("00").build();
    DBContainer container = query.getContainer();
    container.set("EXCONO", xxCONO);
    container.set("EXPANR", panr);
    if (!query.readLock(container, updateCallBack)) {
      mi.error("Record does not exists");
      return;
    }
  }
  
  /*
   * updateCallBack - Callback function to update EXTSCL table
   *
   */
  Closure<?> updateCallBack = { LockedResult lockedResult ->
    
    ZoneId zid = ZoneId.of("Australia/Sydney"); 
    currentDate = LocalDate.now(zid).format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger();
    currentTime = Integer.valueOf(LocalDateTime.now(zid).format(DateTimeFormatter.ofPattern("HHmmss")));
  
    if (!msg1.isEmpty()) {
      lockedResult.set("EXMSG1", msg1);
    }
    if (!msg2.isEmpty()) {
      lockedResult.set("EXMSG2", msg2);
    }
  
    if (!stat.isEmpty()) {
      lockedResult.set("EXSTAT", stat);
    }
  
    if (!grwe.isEmpty()) {
      lockedResult.set("EXGRWE", grwe.toDouble());
    }
  
    lockedResult.set("EXCHNO", lockedResult.get("EXCHNO").toString().toInteger() +1);
    lockedResult.set("EXCHID", program.getUser());
    lockedResult.set("EXRGDT", currentDate);
    lockedResult.set("EXLMDT", currentDate);
    lockedResult.set("EXRGTM", currentTime);
    lockedResult.update();
  }
}