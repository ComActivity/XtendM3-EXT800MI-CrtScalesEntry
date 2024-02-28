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

public class Get extends ExtendM3Transaction {
  private final MIAPI mi;
  private final DatabaseAPI database;
  private final MICallerAPI miCaller;
  private final LoggerAPI logger;
  private final ProgramAPI program;
  private final IonAPI ion;
  
  //Input fields
  private String panr;
  private int xxCONO;
   
 /*
  * Get Purchase Authorisation extension table row
 */
  public Get(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program, IonAPI ion) {
    this.mi = mi;
    this.database = database;
    this.miCaller = miCaller;
    this.logger = logger;
    this.program = program;
    this.ion = ion;
  }
  
  /*
  * Get Scales Results extension records from  EXTSCL
  *
  */
  
  public void main() {
    panr = mi.inData.get("PANR") == null ? '' : mi.inData.get("PANR").trim();
    if (panr == "?") {
      panr = "";
    }
    if (panr.isEmpty()) {
      mi.error("Container number must be entered");
      return;
    }
    xxCONO = (Integer)program.LDAZD.CONO;
    
    DBAction query = database.table("EXTSCL").index("00").selection("EXPANR", "EXMSG1", "EXMSG2", "EXSTAT").build();
    DBContainer container = query.getContainer();
    container.set("EXCONO", xxCONO);
    container.set("EXPANR", panr);
    if (query.read(container)) {
      mi.outData.put("CONO", xxCONO.toString());
      mi.outData.put("PANR", container.get("EXPANR").toString());
      mi.outData.put("MSG1", container.get("EXMSG1").toString());
      mi.outData.put("MSG2", container.get("EXMSG2").toString());
      mi.outData.put("GRWE", container.get("EXGRWE").toString());
      mi.outData.put("STAT", container.get("EXSTAT").toString());
      mi.write();
    } else {
      mi.error("Record does not exist in EXTSCL.");
      return;
    }
  }
}