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

 /*
 *Modification area - M3
 *Nbr               Date      User id     Description
 *BF_R_2001         20241015  DRIESR      Add/Update text attribute  in ATS101 for multiple lot numbers
 */

public class EXT851 extends ExtendM3Batch {
  private final LoggerAPI logger;
  private final DatabaseAPI database;
  private final BatchAPI batch;
  private final MICallerAPI miCaller;
  private final ProgramAPI program;

  private String divi;
  private String inPYNO;
  private String inATID;
  private String inBREF;
  private String inITNO;
  private String inATVA;
  private String inTXT1;
  private String inATNR;
  private int XXCONO;
  private String noseries01;
  private String txid;
  private String faci;
  private String kfld;
  private String oITNO;
  private String oBANO;
  private String oATNR;
  private String lino;
  private String tx60;
  private String oAVSQ;
  private String oANSQ;
  private String oTXID;
  private List lstMatchedRecords;

  public EXT851(LoggerAPI logger, DatabaseAPI database, BatchAPI batch, MICallerAPI miCaller, ProgramAPI program) {
    this.logger = logger;
    this.database = database;
    this.batch = batch;
  	this.miCaller = miCaller;
  	this.program = program;
  }

  public void main() {

    XXCONO= program.LDAZD.CONO;
    divi = "";
    faci = "";
    inITNO = "";
    inBREF = "";
    inATID = "";
    inATVA = "";
    inTXT1 = "";

    if (!batch.getReferenceId().isPresent()) {
      logger.debug("Job data for job ${batch.getJobId()} is missing");
      return;
    }

    // Get parameters from EXTJOB
    Optional<String> data = getJobData(batch.getReferenceId().get())

    if (!data.isPresent()) {
      return
    }

    String rawData = data.get();
    String[] str;
    str = rawData.split(',');

    if (str.length > 1) {
      divi = str[0];
      faci = str[1];
      inITNO = str[2];
      inBREF = str[3];
      inATID = str[4];
      inATVA = str[5];
      inTXT1 = str[6];
    }

    lstMatchedRecords = new ArrayList();
    ExpressionFactory expression = database.getExpressionFactory("MILOMA");
    expression = expression.eq("LMBREF", inBREF);
    expression = expression.and(expression.eq("LMFACI", faci));
    DBAction query = database.table("MILOMA").index("00").matching(expression).selection("LMCONO", "LMBANO", "LMATNR").build();
    DBContainer container = query.getContainer();
    container.set("LMCONO", XXCONO);
    query.readAll(container, 1, 1000, releasedItemProcessor);
  }

  /*
	 * getJobData
	 *
	*/
  private Optional<String> getJobData(String referenceId) {
    DBAction queryEXTJOB = database.table("EXTJOB").index("00").selection("EXRFID", "EXDATA").build();
    DBContainer EXTJOB = queryEXTJOB.createContainer();
    EXTJOB.set("EXCONO", XXCONO);
    EXTJOB.set("EXRFID", referenceId);
    if (queryEXTJOB.read(EXTJOB)) {
      return Optional.of(EXTJOB.getString("EXDATA"));
    }
    return Optional.empty();
  }

  /*
   * reading MILOMA and process attibutes
   *
  */
  Closure<?> releasedItemProcessor = { DBContainer MILOMA ->
    oITNO = MILOMA.get("LMITNO").toString().trim();
    oBANO = MILOMA.get("LMBANO").toString().trim();
    oATNR = MILOMA.get("LMATNR").toString().trim();

    DBAction query = database.table("MIATTR").index("00").selection("AGAVSQ", "AGANSQ", "AGTXID").build();
    DBContainer container = query.getContainer();
    container.set("AGCONO", XXCONO);
    container.set("AGATNR", oATNR.toLong());
    container.set("AGATID", 'CHM04');

    query.readAll(container, 3, 1000, releasedItemProcessor2);
	  kfld = oATNR.padLeft(17,'') + 'CHM04';

    if (oTXID.toInteger() == 0) {
      Map<String, String> params = ["FILE": "MSYTXH"]; // toString is needed to convert from gstring to string
      Closure<?> callback = {
      Map<String, String> response ->
      if(response.TXID != null){
        txid = response.TXID.trim();
      }
    }
    miCaller.call("CRS980MI","RtvNewTextID", params, callback);

    //addtexthead
	  Map<String, String> params01 = ["TXID": txid, "FILE": 'MIATTR00', "KFLD": kfld, "USID": program.getUser(), "TFIL": 'MSYTXH'];
    Closure<?> callback01 = {
      Map<String, String> response ->
    }
    miCaller.call("CRS980MI","AddTxtBlockHead", params01, callback01);

	  //addtextbockline
	  for (int i = 0; i < inTXT1.readLines().size(); i++) {
      lino = (i + 1).toString();
      tx60 = inTXT1.readLines().get(i);

	    Map<String, String> params02 = ["TXID": txid, "LINO": lino, "TX60": tx60, "TFIL": 'MSYTXH', "FILE": 'MIATTR00'];
      Closure<?> callback02 = {
        Map<String, String> response ->
      }
      miCaller.call("CRS980MI","AddTxtBlockLine", params02, callback02);
	  }

    //settextid
    Map<String, String> params08 = ["FILE": "MIATTR00", "TXID": txid, "KV01": XXCONO.toString(), "KV02": oATNR.toString(), "KV03": "CHM04", "KV04": oAVSQ.toString(), "KV05": oANSQ.toString()];
	   Closure<?> callback08 = {
      Map<String, String> response ->
    }
    miCaller.call("CRS980MI","SetTextID", params08, callback08);
    } else {
      Map<String, String> params50 = ["TXID": oTXID, "TFIL": 'MSYTXH'];
      Closure<?> callback50 = {
        Map<String, String> response ->
      }
      miCaller.call("CRS980MI","DltTxtBlockLins", params50, callback50);

	    Map<String, String> params51 = ["TXID": oTXID, "FILE": 'MIATTR00', "KFLD": kfld, "USID": program.getUser(), "TFIL": 'MSYTXH'];
      Closure<?> callback51 = {
        Map<String, String> response ->
      }
      miCaller.call("CRS980MI","AddTxtBlockHead", params51, callback51);

	    for (int i = 0; i < inTXT1.readLines().size(); i++) {
        lino = (i + 1).toString();
        tx60 = inTXT1.readLines().get(i);

	      Map<String, String> params52 = ["TXID": oTXID, "LINO": lino, "TX60": tx60, "TFIL": 'MSYTXH', "FILE": 'MIATTR00'];
        Closure<?> callback52 = {
          Map<String, String> response ->
        }
        miCaller.call("CRS980MI","AddTxtBlockLine", params52, callback52);
	    }
    }
  }

  /*
	 * get attribute values from MIATTR
	 *
	*/

  Closure<?> releasedItemProcessor2 = { DBContainer MIATTR ->
    oAVSQ = MIATTR.get("AGAVSQ").toString().trim();
    oANSQ = MIATTR.get("AGANSQ").toString().trim();
    oTXID = MIATTR.get("AGTXID").toString().trim();
  }
}
