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
 
 import groovy.lang.Closure;
 import java.time.LocalDate;
 import java.time.LocalDateTime;
 import java.time.format.DateTimeFormatter;
 import groovy.json.JsonSlurper;
 import java.math.BigDecimal;
 import java.math.RoundingMode;
 import java.text.DecimalFormat;
 import java.time.ZoneId;
 
/*
 *Modification area - M3
 *Nbr               Date      User id     Description
 *BF_R_2001         20241015  DRIESR      Add/Update Attributes in ATS101 for multiple lot numbers
 *
 */
 
public class EXT850 extends ExtendM3Batch {
  private final LoggerAPI logger;
  private final DatabaseAPI database;
  private final BatchAPI batch;
  private final MICallerAPI miCaller;
  private final ProgramAPI program;
  private final IonAPI ion;
  
  private String divi;
  private String faci;
  private String inPYNO;
  private String inATID;
  private String inBREF;
  private String inITNO;
  private String inATVA;
  private String inATNR;
  private String inTXT1;
  private String errorM;
  private int XXCONO;
  private String noseries01; 
  private boolean scenario1Matched;
  private boolean scenario2Matched;
  private boolean scenario3Matched;
  private String CINO2;
  private String oFEID;
  private String oFNCN;
  private String oVTXT;
  private String oIVNO;
  private String oORNO;
  private String oEXIN;
  private String oINPX;
  private String oYREF;
  private int processed;
  private int maxrecs;
  private String carrier;
  private String loader;
  private String agreement;
  private String supplier;
  private String oITNO;
  private String oBANO;
  private String oATNR;
  private String savedCUNO;
  private String savedPYNO;
  private String savedCINO;
  private String savedCUAM;
  private String savedINYR;
  
 
  private int lineNo;
  
  public EXT850(LoggerAPI logger, DatabaseAPI database, BatchAPI batch, MICallerAPI miCaller, ProgramAPI program, IonAPI ion) {
    this.logger = logger;
    this.database = database;
    this.batch = batch;
  	this.miCaller = miCaller;
  	this.program = program;
  	this.ion = ion;
  }
  
  public void main() {
    
    XXCONO= program.LDAZD.CONO;
    divi = "";
    inPYNO = "";
    inATID = "";
    inBREF = "";
    inITNO = "";
    inATVA = "";
    inATNR = "";
    errorM = '';
    
    if (!batch.getReferenceId().isPresent()) {
      return;
    }
    
    Optional<String> data = getJobData(batch.getReferenceId().get());
    
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
    } 
    
    noseries01 = "";
    processed = 0;
    
    ExpressionFactory expression = database.getExpressionFactory("MILOMA");
    expression = expression.eq("LMCONO", XXCONO.toString());
    expression = expression.and(expression.eq("LMBREF", inBREF));
    DBAction query = database.table("MILOMA").index("00").matching(expression).selection("LMCONO", "LMITNO", "LMBANO", "LMATNR").build();      
    DBContainer container = query.getContainer();
    container.set("LMCONO", XXCONO);
    container.set("LMITNO", inITNO);
    query.readAll(container, 2, 1000, releasedItemProcessor);
  }
  
  /*
	 * getJobData 
	 *
	*/
  private Optional<String> getJobData(String referenceId) {
    def queryEXTJOB = database.table("EXTJOB").index("00").selection("EXRFID", "EXJOID", "EXDATA").build();
    def EXTJOB = queryEXTJOB.createContainer();
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
 
    def params = ["ATNR": oATNR, "ATID": inATID, "ATVA": inATVA]; 
    def callback = {
      Map<String, String> response ->
      errorM = response.errorMessage; 
    }
    miCaller.call("ATS101MI","SetAttrValue", params, callback);	
	  
	  if(processed == 0 && errorM != null) { processerror(errorM);
	  }
	  
	  def params01 = ["ATNR": oATNR]; 
    
    def callback01 = {
      Map<String, String> response ->
      if(response.ATID.trim().equals("REC04")){
        carrier = response.ATVA;  
      }
      if(response.ATID.trim().equals("REC05")){
        loader = response.ATVA;  
      }
      if(response.ATID.trim().equals("SUP01")){
        supplier = response.ATVA;  
      }
      if(response.ATID.trim().equals("AGT01")){
        agreement = response.ATVA;  
      }
    }
    miCaller.call("ATS101MI","GetAttributes", params01, callback01);	 

    if(carrier != null && supplier != null && agreement != null) {setLot(loader, carrier, supplier, agreement);
    } 
  }
  
  /*
   * Process/distribute grading error report   
   *
  */
  
  private void processerror(String errorm) {
    def params60 = ["REPO": 'GRADE01', "REPV": 'GRADE01', "SUBJ": 'Error in S/Docket ' + inBREF, "EMTX": errorM, "SF1F": oBANO, "SF1T": oBANO ]; 
    def callback60 = {
      Map<String, String> response ->
    }
    miCaller.call("AHS150MI","Submit", params60, callback60);	
    processed = 1;
  }

  /*
   * Set loader/carrier/agreementno on lotnumber MILOMA   
   *
   */
  private void setLot(String loader, String carrier, String supplier, String agreement) {
    def params02 = ["ITNO": oITNO, "BANO": oBANO, "ARLA": loader.trim(), "CFI2": carrier.trim(), "CFI3": agreement.trim(), "SUNO": supplier.trim() ]; 
    def callback02 = {
      Map<String, String> response ->
    }
    miCaller.call("MMS235MI","UpdItmLot", params02, callback02);	
  }
}
