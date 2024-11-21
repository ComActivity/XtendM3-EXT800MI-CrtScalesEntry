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

 import java.time.LocalDate;
 import java.time.LocalDateTime;
 import java.time.format.DateTimeFormatter;

 /*
 *Modification area - M3
 *Nbr               Date      User id     Description
 *BF_R_2001         20241015  DRIESR      Add/Update Attributes in ATS101 for multiple lot numbers
 *
 */

 /*
  * Add/Update Attributes for Grading 2.0 H5 SDK
 */
public class AddAttributes extends ExtendM3Transaction {
  private final MIAPI mi;
  private final DatabaseAPI database;
  private final MICallerAPI miCaller;
  private final LoggerAPI logger;
  private final ProgramAPI program;
  private final IonAPI ion;

  private String divi;
  private String faci;
  private String bref;
  private String atid;
  private String itno;
  private String atva;
  private String txt1;
  private int XXCONO;

  public AddAttributes(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program,  IonAPI ion) {
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
  	bref = mi.inData.get("BREF") == null ? '' : mi.inData.get("BREF").trim();
  	if (bref == "?") {
  	  bref = "";
  	}
  	itno = mi.inData.get("ITNO") == null ? '' : mi.inData.get("ITNO").trim();
  	if (itno == "?") {
  	  itno = "";
  	}
  	atid = mi.inData.get("ATID") == null ? '' : mi.inData.get("ATID").trim();
  	if (atid == "?") {
  	  atid = "";
  	}
  	atva = mi.inData.get("ATVA") == null ? '' : mi.inData.get("ATVA").trim();
  	if (atva == "?") {
  	  atva = "";
  	}
  	txt1 = mi.inData.get("TXT1") == null ? '' : mi.inData.get("TXT1").trim();
  	if (txt1 == "?") {
  	  txt1 = "";
  	}

  	if (divi.isEmpty()) {
      mi.error("division must be entered");
      return;
    }
    if (faci.isEmpty()) {
      mi.error("facility must be entered");
      return;
    }
    if (bref.isEmpty()) {
      mi.error("lot reference must be entered");
      return;
    }

    if (atid.equals("CHM04") && txt1.isEmpty()) {
      mi.error("comments must be entered");
      return
    }

    if (!atid.equals("CHM04") && !atid.isEmpty() && atva.isEmpty()) {
       mi.error("attribute value must be entered");
      return;
    }

    XXCONO= program.LDAZD.CONO;

     // - validate item number
    if (!itno.isEmpty()) {
      DBAction queryMITMAS = database.table("MITMAS").index("00").selection("MMITNO").build();
      DBContainer MITMAS = queryMITMAS.getContainer();
      MITMAS.set("MMCONO", XXCONO);
      MITMAS.set("MMITNO", itno.trim());
      if (!queryMITMAS.read(MITMAS)) {
        mi.error("item number does not exist.");
        return;
      }
    }

    // - validate facility
    if (!faci.isEmpty()) {
      DBAction queryCFACIL = database.table("CFACIL").index("00").build();
      DBContainer CFACIL = queryCFACIL.getContainer();
      CFACIL.set("CFCONO", XXCONO);
      CFACIL.set("CFFACI", faci);
      if(!queryCFACIL.read(CFACIL)) {
        mi.error("facility does not exist.");
        return;
      }
    }

    // - validate attribute ID
    if (!atid.isEmpty()) {
      DBAction queryMATRMA = database.table("MATRMA").index("00").build();
      DBContainer MATRMA = queryMATRMA.getContainer();
      MATRMA.set("AACONO", XXCONO);
      MATRMA.set("AAATID", atid);
      if(!queryMATRMA.read(MATRMA)) {
        mi.error("attribute ID does not exist.");
        return;
      }
    }

    // - validate division
    if (!divi.isEmpty()) {
      DBAction queryCMNDIV = database.table("CMNDIV").index("00").build();
      DBContainer CMNDIV = queryCMNDIV.getContainer();
      CMNDIV.set("CCCONO", XXCONO);
      CMNDIV.set("CCDIVI", divi);
      if(!queryCMNDIV.read(CMNDIV)) {
        mi.error("Division does not exist.");
        return;
    }
  }

  String referenceId = UUID.randomUUID().toString();
  setupData(referenceId);

  if (atid.equals("CHM04")) {
    def params = ["JOB": "EXT851", "TX30": "UpdateComments", "XCAT": "010", "SCTY": "1", "XNOW": "1", "UUID": referenceId]; // ingle run - now
    miCaller.call("SHS010MI", "SchedXM3Job", params, { result -> });
    } else {
      def params = ["JOB": "EXT850", "TX30": "AddAttributes", "XCAT": "010", "SCTY": "1", "XNOW": "1", "UUID": referenceId]; // ingle run - now
      miCaller.call("SHS010MI", "SchedXM3Job", params, { result -> });
    }
  }
  /*
	 * setupData  - write to EXTJOB
	 *
	 */
  private void setupData(String referenceId) {
    String data = "";
    if (!divi.isEmpty()) {
      data = divi + ',' + faci + ',' + itno + ',' + bref + ',' + atid + ',' + atva + ',' + txt1;
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
