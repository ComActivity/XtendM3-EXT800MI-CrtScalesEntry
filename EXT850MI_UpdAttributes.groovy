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
 
 
 /*
 *Modification area - M3
 *Nbr               Date      User id     Description
 *BF_R_2001         20241015  DRIESR      Update Attributes in batch mode
 *
 */
 
 /*
  * AP paymnent split night run
 */
public class UpdAttributes extends ExtendM3Transaction {
  private final MIAPI mi;
  private final DatabaseAPI database;
  private final MICallerAPI miCaller;
  private final LoggerAPI logger;
  private final ProgramAPI program;
  private final IonAPI ion;

  private String divi;
  private String faci;
  private String xnow;
  private String bref;
  private String atid;
  private String xjtm;
  private String itno;
  private String atnr;
  private String atva;
  private int XXCONO;
  
  public UpdAttributes(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program,  IonAPI ion) {
    this.mi = mi;
    this.database = database;
    this.miCaller = miCaller;
    this.logger = logger;
    this.program = program;
    this.ion = ion;

  }
  
  public void main() {
    divi = mi.inData.get("DIVI") == null ? '' : mi.inData.get("DIVI").trim();
  	if (divi == "?") {
  	  divi = "";
  	} 
  	
  	faci = mi.inData.get("FACI") == null ? '' : mi.inData.get("FACI").trim();
  	if (faci == "?") {
  	  faci = "";
  	}
  	
  	itno = mi.inData.get("ITNO") == null ? '' : mi.inData.get("ITNO").trim();
  	if (itno == "?") {
  	  itno = "";
  	}
  	
  	bref = mi.inData.get("BREF") == null ? '' : mi.inData.get("BREF").trim();
  	if (bref == "?") {
  	  bref = "";
  	}
  	
  	atid = mi.inData.get("ATID") == null ? '' : mi.inData.get("ATID").trim();
  	if (atid == "?") {
  	  atid = "";
  	}
  	
  
  	atva = mi.inData.get("ATVA") == null ? '' : mi.inData.get("ATVA").trim();
  	if (atva == "?") {
  	  atva = "";
  	}
  	
  
  	if (divi.isEmpty()) {
      mi.error("Division must be entered");
      return;
    }
    //A WZHAO 20230328 - new input parameter XJTM
  	xjtm = mi.inData.get("XJTM") == null ? '' : mi.inData.get("XJTM").trim();
  	if (xjtm == "?") {
  	  xjtm = "";
  	}
    XXCONO= program.LDAZD.CONO;
    
    DBAction queryCMNDIV = database.table("CMNDIV").index("00").selection("CCDIVI").build();
    DBContainer CMNDIV = queryCMNDIV.getContainer();
    CMNDIV.set("CCCONO", XXCONO);
    CMNDIV.set("CCDIVI", divi);
    if(!queryCMNDIV.read(CMNDIV)) {
      mi.error("Division does not exist.");
      return;
    } 
  	String referenceId = UUID.randomUUID().toString();
    setupData(referenceId);
      def params = ["JOB": "EXT850", "TX30": "UpdateAttributes", "XCAT": "010", "SCTY": "1", "XNOW": "1", "UUID": referenceId]; // ingle run - now
      miCaller.call("SHS010MI", "SchedXM3Job", params, { result -> });
     
  }
  /*
	 * setupData  - write to EXTJOB
	 *
	 */
  private void setupData(String referenceId) {
    String data = "";
    if (!divi.isEmpty()) {
      data = divi + ',' + faci + ',' + itno + ',' + bref + ',' + atid + ',' + atva;
    }
    int currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger();
  	int currentTime = Integer.valueOf(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
  	
    DBAction actionEXTJOB = database.table("EXTJOB").build();
  	DBContainer EXTJOB = actionEXTJOB.getContainer();
  	EXTJOB.set("EXCONO", XXCONO);
  	EXTJOB.set("EXRFID", referenceId);
  	EXTJOB.set("EXDATA", data);
    EXTJOB.set("EXRGDT", currentDate);
  	EXTJOB.set("EXRGTM", currentTime);
  	EXTJOB.set("EXLMDT", currentDate);
  	EXTJOB.set("EXCHNO", 0);
  	EXTJOB.set("EXCHID", program.getUser());
    actionEXTJOB.insert(EXTJOB);
  }
}