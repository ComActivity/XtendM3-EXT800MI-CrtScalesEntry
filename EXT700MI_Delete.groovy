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


public class Delete extends ExtendM3Transaction {
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
  * Delete Scales dashboard extension table row
 */
  public Delete(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program, IonAPI ion) {
    this.mi = mi;
    this.database = database;
    this.miCaller = miCaller;
    this.logger = logger;
    this.program = program;
    this.ion = ion;
  }
  /*
  * Main program to delete row from table
  */
  public void main() {
    panr = mi.inData.get("PANR") == null ? '' : mi.inData.get("PANR").trim();
    if (panr == "?") {
      panr = "";
    }

    // Validate input fields
    if (panr.isEmpty()) {
      mi.error("Container nomust be entered");
      return;
    }
    
    xxCONO = (Integer)program.getLDAZD().CONO;
    DBAction queryEXTSCL = database.table("EXTSCL").index("00").selection("EXPANR").build();
    DBContainer EXTSCL = queryEXTSCL.getContainer();
    EXTSCL.set("EXCONO", xxCONO);
    EXTSCL.set("EXPANR", panr);
    if (!queryEXTSCL.readLock(EXTSCL, deleteCallBack)) {
      mi.error("Record does not exist");
      return;
    }
  }
  
  /*
   * deleteCallBack - Callback function to delete EXTSCL table
   *
  */
  Closure<?> deleteCallBack = { LockedResult lockedResult ->
    lockedResult.delete();
  }
}