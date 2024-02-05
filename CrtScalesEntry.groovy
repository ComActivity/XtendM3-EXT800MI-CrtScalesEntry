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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import groovy.json.JsonSlurper
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.lang.GroovyObject


/*
 *Modification area - M3
 *Nbr               Date      User id     Description
 *BFG_100        20240117     RDRIESSEN   Scales Integration from IOT into Infor M3
 *
 */


public class CrtScalesEntry extends ExtendM3Transaction {
  private final MIAPI mi
  private final MICallerAPI miCaller
  private final LoggerAPI logger
  private final DatabaseAPI database
  private final ProgramAPI program
  private final IonAPI ion
  
  public CrtScalesEntry(MIAPI mi, MICallerAPI miCaller, LoggerAPI logger,DatabaseAPI database, ProgramAPI program,IonAPI ion) {
    this.mi = mi
    this.miCaller = miCaller
    this.logger = logger
    this.database = database
    this.program = program
    this.ion = ion
  }
  
  private String  panr //Package Number - Bin Number
  private String  grwestr //Gross Weight as String During Input
  private String  grwe //Gross Weight
  private String  orsortItem //Variety Item number
  private String  ordlmoTareweight //Tare Weight
  private String  orpan1Supplier //Supplier
  private String  orpan2Deliveryno //Delivery No
  private String  orpan3Lotno //Lot Number
  private String  puno // PO Number
  private Integer pnli //PO Line
  private Integer pnls //PO SubLine
  private Integer pnlx //PO Line Suffix
  private String  pusl //PO Status
  private Integer xxcono //Current Company for user executing it
  private String  xxuser //Current User
  private String  currentDate
  private String  currentTime
  private String  carrier 
  private String  loader
  private String  fruitgrade
  private String  newitem
  private String  pkgmesg
  private Double  tareweight
  private Double  grossweight
  
  public void main() {
    // Declare Variables for Main 
    
    boolean poExists       = false
    boolean addPOline      = false
    Integer poStatus       = 0
    boolean preValid       = false
    boolean addNewPO       = false
    boolean getPOCharges   = false
    boolean receiptPO      = false
    boolean getaddedPOLine = false
    boolean getNewItem     = false
    boolean addreclass     = false
    boolean initPackageDetails = false
    String  initExtscl     = null
    boolean updExtscl      = false
    Integer netweight      = 0
    
    
    //Gather API Inputs
    panr = mi.inData.get("PANR") == null ? '' : mi.inData.get("PANR").trim() // Input Param Package No
    grwe = mi.inData.get("GRWE") == null ? '' : mi.inData.get("GRWE").trim() // Input Param Gross Weight
    
    grossweight = Double.parseDouble(grwe)/1000
    
    
    // Set CONO for Current User Session
    xxcono = (Integer)program.LDAZD.CONO // Retrieve users Current CONO
    xxuser = program.getUser()
    //logger.info("CONO=${xxcono}:USID=${xxuser}")
    
    //Set Current Date and time
    ZoneId zid = ZoneId.of("Australia/Sydney") 
    currentDate = LocalDate.now(zid).format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger()
    currentTime = Integer.valueOf(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")))
    
    
    preValid = preValidProc() // Precheck required M3 componets exist
    //logger.info("PreValidation Result is ${preValid}")
    if(preValid){
      initExtscl = EXT700MIAddProc(panr,"", "",grwe,"05") //Insert into EXTSCL Status = 05
      initPackageDetails = initPackageDetailsProc() // Retrieve values required for program from MPTRNS using API init Variables
      //logger.info("Init Package Details Result is ${initPackageDetails} and ${initExtscl}")
      if(initPackageDetails && (initExtscl == "updated")){
        updExtscl = EXT700MIUpdateproc(panr,"${pkgmesg}","Passed",grwe,"10")
        netweight = (Integer.parseInt(grwe) - Integer.parseInt(ordlmoTareweight))// Netweight is the Gross - Tare,  PO is created and received with Netweight
        tareweight = Double.parseDouble(ordlmoTareweight)/1000
        //logger.info("Netweight=${netweight}")
        if(netweight > 0){ // Netweight has to be greater then 0
          //logger.info("Docket Number is ${orpan2Deliveryno}")
          poExists = poExistsProc() // IF there is an existing PO with this delivery number and status of pos is less then 75
          if(poExists){ 
            //logger.info("PO Exist = ${poExists}")
            //logger.info("Adding PO Line")
            addPOline = addPOlineProc(netweight)
            getaddedPOLine = getaddedPOLineProc()
            if(addPOline && getaddedPOLine){ // When a PO Line is added via PPS200MI it is immediately created unlike PPS370MI Batch
              updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Line Created","Passed",grwe,"20")
              //logger.info("Exisiting PO for charges to be Applied to Purchase order ${puno}/${pnli}")
              getPOCharges = getPOChargesProc() // Retrieve Attribute for Carrier and Loader Payments
              //logger.info("getPOCharges = ${getPOCharges}")
              if(getPOCharges){
                updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Charges Added","Passed",grwe,"30")
                receiptPO = PPS001MIReceiptProc(netweight) // Now that we have a valid PO whether new or and added line with charges updated we can receipt the PO
              }else{
                //logger.info("Failed to Apply PO Charges when PO Line added to exisitng PO")
                updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Charges Added","Failed",grwe,"99")
                return
              }
            }
            else{
              updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Line Created","Failed",grwe,"99")
              return
            }
          }
          else{
            //logger.info("No Existing PO Adding New PO")
            addNewPO = addNewPOProc(netweight)
            if(addNewPO){ // When a PO Line is added via PPS370MI Batch we need to wait until the PO has been created hence the while loop
              updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Line Created","Passed",grwe,"20")
              //logger.info("New PO for charges to be Applied")
              Integer polinecreated = 0
        
              while(polinecreated != 15) {
                polinecreated = poStatusProc()
              }
              getPOCharges = getPOChargesProc()// Retrieve Attribute for Carrier and Loader Payments
              //logger.info("getPOCharges = ${getPOCharges}")
              if(getPOCharges){
                updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Charges Added","Passed",grwe,"30")
                receiptPO = PPS001MIReceiptProc(netweight) // Now that we have a valid PO whether new or and added line with charges updated we can receipt the PO
              }else{
                //logger.info("Failed to Apply PO Charges when PO Line added to exisitng PO")
                updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Charges Added","Failed",grwe,"99")
                return
              }
            }else{
              updExtscl = EXT700MIUpdateproc(panr,"Purchase Order Line Created","Failed",grwe,"99")
              return
            }
          }
          if(receiptPO){ // IF the PO has been receipted Reclassify the item
            updExtscl = EXT700MIUpdateproc(panr,"Receipt of Purchase Order","Passed",grwe,"75")
            getNewItem = getNewItemProc()
            if(getNewItem){
              updExtscl = EXT700MIUpdateproc(panr,"Item Search","TRUE",grwe,"75")
              //logger.info("New Item to reclassify to is ${newitem} FruitGrade = ${fruitgrade}")
              addreclass = MMS850MIAddReclassProc()
              if(addreclass){
                updExtscl = EXT700MIUpdateproc(panr,"Item Reclassified","Passed",grwe,"90")
                mi.outData.put("PUNO", "${puno}")
                mi.outData.put("PNLI", "${pnli}")
                mi.outData.put("NTNO", "${newitem}")
                mi.outData.put("STAT", "OK")
                mi.write()
              }else{
                updExtscl = EXT700MIUpdateproc(panr,"Item Reclassified","Failed",grwe,"99")
                mi.outData.put("STAT", "NOK")
                mi.outData.put("MSGN", "Item Reclassify Failed")
              }
            }else{
              //logger.info("Failed to find new item")
              updExtscl = EXT700MIUpdateproc(panr,"New Item Search","Failed",grwe,"99")
              mi.outData.put("STAT", "NOK")
              mi.outData.put("MSGN", "New Item Search Failed")
              mi.write()
              return
            }
          }else{
            updExtscl = EXT700MIUpdateproc(panr,"Receipt of Purchase Order","Failed",grwe,"99")
            mi.outData.put("STAT", "NOK")
            mi.outData.put("MSGN", "Purchase Order Receipt Failed")
            mi.write()
            return
          }
        }else{
           updExtscl = EXT700MIUpdateproc(panr,"Net Weight","Failed",grwe,"99")
           mi.outData.put("STAT", "NOK")
           mi.outData.put("MSGN", "Netweight Invalid")
           mi.write()
           return
        }
      }else{
        if (initExtscl == "completed"){
          updExtscl = EXT700MIUpdateproc(panr,"Already Processed","","","90")
          mi.outData.put("STAT", "NOK")
          mi.outData.put("MSGN", "Already Processed")
          mi.write()
        }else{
          updExtscl = EXT700MIUpdateproc(panr,"${pkgmesg}","Failed",grwe,"99")
          mi.outData.put("STAT", "NOK")
          mi.outData.put("MSGN", "InititPkgDetails Failed")
          mi.write()
        }
        return
      }//End InitPkgDetails
    }else{
      //logger.info("Validation of Inputs for PANR and GRWE failed")
      mi.outData.put("STAT", "NOK")
      mi.outData.put("MSGN", "PANR and GRWE Invalid")
      mi.write()
      return
    }// End Validation of Inputs
  }//End Main
  
 /*
  * Validates Inputs from API call
  *
  */
  
  boolean preValidProc(){
    //logger.info("PANR=${panr} and GRWE = ${grwe}")
    if (panr.isEmpty()) {
      mi.error("Package Number Must be entered")
      return false
    }
    if (grwe.isEmpty()) {
      mi.error("Gross Weight Must be entered")
      return false
    }
    return true
    
  }
  
  /*
  * Values being initialised in this function are prevalidated by Mobile Action Software
  *
  */
  
  boolean initPackageDetailsProc(){
    //logger.info("Initalizing Variable of Package Details for Package Number ${panr}")
    orsortItem = null
    ordlmoTareweight = null
    orpan1Supplier = null
    orpan2Deliveryno = null
    orpan3Lotno = null
    boolean validpkg = true
    boolean updExtscl = false
    Map<String,String> params= ["PANR":"${panr}".toString()] // toString is needed to convert from gstring to string
    
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        validpkg = false
        pkgmesg = "Package Exists"
        //logger.info("MMS470MI LstPackageStk Response = ${response.errorMessage}")
        //mi.error(response.errorMessage)
      }
      //logger.info("MMS470MI LstPackageStk Response = ${response}")
      
      if(response.SORT != null){
        orsortItem = response.SORT.trim()
      }
      if(response.DLMO != null){
        ordlmoTareweight = response.DLMO.trim()
      }
      if(response.PAN1 != null){
        orpan1Supplier = response.PAN1.trim()
      }
      if(response.PAN2 != null){
        orpan2Deliveryno = response.PAN2.trim()
      }
      if(response.PAN3 != null){
        orpan3Lotno = response.PAN3.trim()
      }
    }
    
    miCaller.setListMaxRecords(1)
    miCaller.call("MMS470MI","LstPackageStk", params, callback)

    if(validpkg){
      pkgmesg = "Package Details"
      if(orsortItem.isEmpty() || ordlmoTareweight.isEmpty() || orpan1Supplier.isEmpty() || orpan2Deliveryno.isEmpty() || orpan3Lotno.isEmpty()){
        return false
      }
      else{
        return true
      }
    }else{
      return false
    }
  }
  
  
  /*
  * Check if delivery exists on existing PO
  *
  */
  
  boolean poExistsProc(){
    
    //logger.info("Checking if ${orpan2Deliveryno} exists on an exisitng PO")
    
    DBAction query = database.table("MPLIND").index("10").selection("ICSUDO").build()
    DBContainer container = query.getContainer()
    container.set("ICCONO", xxcono)
    container.set("ICSUDO", orpan2Deliveryno)
    query.readAll(container,2,10000, poDelivery) // Need to check all receipt lines for this delivery until a valid PO with status is found
    //logger.info("Delivery Found On ICPUNO = ${puno} - ICPNLI = ${pnli} - ICPNLS = ${pnls} - ICPNLX = ${pnlx}")
    if (puno != null){
      //logger.info("PO exists")
      return true
    }
    else {
      //logger.info("No existing PO found")
      return false
    }
  }
  
  /*
  * this subroutine confirms the Purchase order has a lowest status of < 75 and returns true
  *
  */

  Integer poStatusProc(){
    // This sub routine confirms the Purchase order has a lowest status of < 75 and returns true
    DBAction query = database.table("MPHEAD").index("00").selection("IAPUSL").build()
    DBContainer container = query.getContainer()
    container.set("IACONO", xxcono)
    container.set("IAPUNO", puno)
    if (query.read(container)) {
      String postatus = container.get("IAPUSL") //Get the lowest status on the Purchase order
      //logger.info("IAPUSL = ${postatus}")
      return Integer.parseInt(postatus) 
    }
  }
  
  
  /*
  * this routine adds a poline to existing order
  *
  */
  
  boolean addPOlineProc(Integer netweight){
    puno=puno.trim()
    
    Map<String,String> headers01 = ["Accept": "application/json"]
    //logger.info("Inputs to API PPS200MI PUNO=${puno} CONO=${xxcono} ITNO=${orsortItem} ORQA=${netweight} SUNO=${orpan1Supplier}" )
    String endpoint = "/M3/m3api-rest/v2/execute/PPS200MI/AddLine"
    Map<String,String> headers = ["Accept": "application/json"]
    Map<String,String> queryParameters = ["cono":"${xxcono}".toString(),"PUNO":"${puno}".toString(),"ITNO":"${orsortItem}".toString(),"ORQA": "${netweight}".toString(),"SUNO":"${orpan1Supplier}".toString(),"PUUN":"KG","PURC":"${xxuser}".toString()] // define as map if there are any query parameters e.g. ["name1": "value1", "name2": "value2"]
    
    IonResponse response = ion.get(endpoint, headers, queryParameters)
    if (response.getError()) {
      logger.debug("Failed calling ION API, detailed error message: ${response.getErrorMessage()}")
      return false
    }
    if (response.getStatusCode() != 200) {
      logger.debug("Expected status 200 but got ${response.getStatusCode()} instead")
      return false
    }
    if (response.getStatusCode() == 200) {
      JsonSlurper jsonSlurper = new JsonSlurper()
      Map<String, Object> miResponse = (Map<String, Object>) jsonSlurper.parseText(response.getContent())
      if(miResponse != null) {
        //logger.info("${miResponse}")
        ArrayList<Map<String, Object>> results = (ArrayList<Map<String, Object>>) miResponse.get("results")
        String succestrns = miResponse["nrOfSuccessfullTransactions"]
        if (succestrns == "1"){
          //logger.info("Nr of Succes Trans = ${succestrns}")
          return true
        }
        else{
          String errormesg = results[0]["errorMessage"]
          mi.error("${errormesg}")
          //logger.info("PPS200MI AddLine Error Response ${errormesg}")
          return false
        }
      }
    }
  }
  
  /*
  * this subroutine confirms the Purchase order line number that has just been added
  *
  */

  boolean getaddedPOLineProc(){
    // This sub routine confirms the Purchase order line number that has been just added
    DBAction query = database.table("MPLINE").index("00").selection("IBPUNO").build()
    DBContainer container = query.getContainer()
    container.set("IBCONO", xxcono)
    container.set("IBPUNO", puno)
    if(query.readAll(container, 2,10000, polinesadded)){ //Need to get to the last PO Line that was added so must real all lines on PO
      return true
    }
    else{
      mi.error("Failed to Read MPLINE")
      return false
    }
  }
  
  /*
  * If no existing PO exists, a new PO must be created using the PPS370MI Batch process
  *
  */

  boolean addNewPOProc(Integer netweight){
    String poMsgn = null
    String newpo   = null
    String newpnli = null
 
    //logger.info("Adding new Purchase Order via PPS370MI")
    poMsgn = pps370MIStartEntryProc()
    //logger.info("PO Messgae Number = ${poMsgn}")
    if(poMsgn != null){
      newpo = pps370MIAddHeadProc(poMsgn)
      //logger.info("New Purchase order Number=${newpo}")
      if(newpo != null){
        newpnli = pps370MIAddLineProc(poMsgn,newpo,netweight)
        //logger.info("New Purchase order line Number=${newpnli}")
        if(newpnli != null){
          //logger.info("ClosingPOFinish")
          pps370MIFinishEntryProc(poMsgn)
          puno = newpo
          pnli = Integer.parseInt(newpnli)
          return true
        }
      }
    }
  }
  String pps370MIStartEntryProc(){
    String poMsgn = null
    Map<String,String> params= ["BAOR":"SBS"] // toString is needed to convert from gstring to string
    
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("PPS370MI StartEntry Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
        poMsgn = null
      }
      if(response.MSGN != null){
        //logger.info("PPS370MI StartEntry Response = ${response}")
        poMsgn = response.MSGN.trim()
      }
    }
    miCaller.call("PPS370MI","StartEntry", params, callback)
    return poMsgn
  }
  String pps370MIAddHeadProc(String mesgn){
    
    String poNumber = null

    Map<String,String> params= ["MSGN":"${mesgn}".toString(),"FACI":"300","WHLO":"V02","SUNO":"${orpan1Supplier}".toString(),"ORTY":"S20","DWDT":"${currentDate}".toString()] // toString is needed to convert from gstring to string
    
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("PPS370MI AddHead Response = ${response.errorMessage}")
        poNumber = null
        mi.error(response.errorMessage)
      }
      if(response.PUNO != null){
        poNumber = response.PUNO.trim()
        //logger.info("PPS370MI Addhead Response = ${response}")
      }
    }
    miCaller.call("PPS370MI","AddHead", params, callback)
    return poNumber
  }
  String pps370MIAddLineProc(String mesgn,String puno, Integer netweight){
    
    String poLine = null

    Map<String,String> params= ["MSGN":"${mesgn}".toString(),"PUNO":"${puno}".toString(),"ITNO":"${orsortItem}".toString(),"ORQA": "${netweight}".toString(),"SUNO":"${orpan1Supplier}".toString(),"DWDT":"${currentDate}".toString(),"PUUN":"KG","PURC":"${xxuser}".toString()] // toString is needed to convert from gstring to string
    
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("PPS370MI AddLine Response = ${response.errorMessage}")
        poLine   = null
        mi.error(response.errorMessage)
      }
      if(response.PUNO != null){
        poLine   = response.PNLI.trim()
        //logger.info("PPS370MI AddLine Response = ${response}")
      }
    }
    miCaller.call("PPS370MI","AddLine", params, callback)
    return poLine
  }
  void pps370MIFinishEntryProc(String mesgn){
    Map<String,String> params= ["MSGN":"${mesgn}".toString()] // toString is needed to convert from gstring to string
    
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("PPS370MI FinishEntry Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
      }
    }
    miCaller.call("PPS370MI","FinishEntry", params, callback)
    //logger.info("PPS370MI FinishEntry Completed")
  }
  
  /*
  * Determine PO Charges from attributes on Lot and Item
  *
  */
  
  boolean getPOChargesProc(){
    String attrnbr = null
    Map<String, String> inputParams = [:]
    boolean attrvalues = false
    boolean updPOCharge = true
    attrnbr = getAttrNbrProc() // Retrieve Atrribute Number from MMS235MI
    if(attrnbr != null){
      //logger.info("Attribute Number = ---${attrnbr}---")
      attrvalues = getAttrValsProc(attrnbr)
      if(attrvalues){ // If the DB API returned results and global vairalbes carrier and loader were assigned
        //logger.info("Carrier = ${carrier} : Loader = ${loader}")
        if(carrier == "99999"){
          inputParams = ["PUNO":"${puno}".toString(),"PNLI":"${pnli}".toString(),"PNLS":"0","EXTY":"2","CDSE":"800","CEID":"CAR02","CEVA":"0"]
          updPOCharge = PPS215MIUpdPOChargeProc(inputParams)
          //logger.info("updPOCharge result = ${updPOCharge}")    
        }
        if(loader == "99999"){
          inputParams = ["PUNO":"${puno}".toString(),"PNLI":"${pnli}".toString(),"PNLS":"0","EXTY":"2","CDSE":"900","CEID":"LOA02","CEVA":"0"]
          updPOCharge = PPS215MIUpdPOChargeProc(inputParams)
          //logger.info("updPOCharge result = ${updPOCharge}")          
        }
        if(loader == "99998"){
          inputParams = ["PUNO":"${puno}".toString(),"PNLI":"${pnli}".toString(),"PNLS":"0","EXTY":"2","CDSE":"900","CEID":"LOA02","OVHE":"2"]
          updPOCharge = PPS215MIUpdPOChargeProc(inputParams)
          //logger.info("updPOCharge result = ${updPOCharge}")    
        }
        return updPOCharge
      }
    }
  }
  String getAttrNbrProc(){
    Map<String,String> params= ["ITNO":"${orsortItem}".toString(),"BANO":"${orpan3Lotno}".toString()] // toString is needed to convert from gstring to string
    String attrnbr = null
    
    def callback = {
      Map<String, String> response ->
       if(response.errorMessage != null){
        //logger.info("MMS235MI GetLotItm Error Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
        attrnbr = null
      }
      if(response.ATNR != null){
        //logger.info("MMS235MI GetLotItm Response = ${response}")
        attrnbr = response.ATNR.trim()
      }
    }
    miCaller.call("MMS235MI","GetItmLot", params, callback)
    return attrnbr
  }
  boolean getAttrValsProc(String attrnbr){
    long attrnbrInt = Long.parseLong("${attrnbr}")
    ExpressionFactory expression = database.getExpressionFactory("MIATTR")
    expression = expression.eq("AGATID", "REC04").or(expression.eq("AGATID", "REC05")).or(expression.eq("AGATID", "DRF01"))
    DBAction query = database.table("MIATTR").index("00").matching(expression).selection("AGCONO", "AGATNR","AGATID","AGATVA").build()
    DBContainer container = query.getContainer()
    container.set("AGCONO", xxcono)
    container.set("AGATNR", attrnbrInt)
    if(query.readAll(container, 2,10000, getAttribValues)){
      return true
    }
    else{
      mi.error("Failed to Read MIATTR")
      return false
    }
  }
  boolean PPS215MIUpdPOChargeProc(Map<String,String> params){
    //logger.info("Inputs for PPS215MI UpdPOCharge= ${params}")
    boolean respok = true
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("PPS215MI UpdPOCharge Error Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
        respok = false
       }
    }
    miCaller.call("PPS215MI","UpdPOCharge", params, callback)
    return respok
  }
  boolean PPS001MIReceiptProc(Integer netweight){
    boolean poReceived = false
    Map<String,String> params= ["CONO":"${xxcono}".toString(),
                  "TRDT":"${currentDate}".toString(),
                  "RESP":"${xxuser}".toString(),
                  "PUNO":"${puno}".toString(),
                  "PNLI":"${pnli}".toString(),
                  "PNLS":"0",
                  "RVQA":"${netweight}".toString(),
                  "WHSL":"PUTAWAY",
                  "BANO":"${orpan3Lotno}".toString(),
                  "CAMU":"${panr}".toString(),
                  "WHLO":"V02",
                  "SUDO":"${orpan2Deliveryno}".toString(),
                  "AT01":"REC01",
                  "AV01":"${grossweight}".toString(),
                  "AT02":"REC02",
                  "AV02":"${tareweight}".toString()
                ]
    //logger.info("Receit PO Inputs = ${params}")
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("PPS001MI Receipt Error Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
        poReceived = false
      }
      if(response != null){
        //logger.info("PPS001MI Receipt Response = ${response}")
        poReceived = true
      }
      
    }
    miCaller.call("PPS001MI","Receipt", params, callback)
    return poReceived
  }
  
  /*
  * this routine retrieves the new item number to be classified to
  *
  */
  
  boolean getNewItemProc(){
    ExpressionFactory expression = database.getExpressionFactory("MPDMAT")
    expression = expression.eq("PMFMT2", "${fruitgrade}")
    DBAction query = database.table("MPDMAT").index("00").matching(expression).selection("PMCONO","PMFACI","PMPRNO","PMMTNO").build()
    DBContainer container = query.getContainer()
    container.set("PMCONO", xxcono)
    container.set("PMFACI", "300")
    container.set("PMPRNO", "${orsortItem}".toString())
    if(query.readAll(container, 3,1, newitems)){
      return true
    }
    else{
      mi.error("Failed to Read MPDMAT")
      return false
    }
  }
  boolean MMS850MIAddReclassProc(){
    boolean reclassified = false
    Map<String,String> params= ["CONO":"${xxcono}".toString(),
                  "PRFL":"*EXE",
                  "E0PA": "WA",
                  "E065": "WAM",
                  "WHLO": "V02",
                  "WHSL": "PUTAWAY",
                  "ITNO": "${orsortItem}".toString(),
                  "BANO": "${orpan3Lotno}".toString(),
                  "CAMU": "${panr}".toString(),
                  "NITN": "${newitem}".toString(),
                  "NBAN": "${orpan3Lotno}".toString(),
                  "STAS": "2"
                ]
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("MMS850MI AddReclass Error Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
        reclassified = false
      }
      if(response != null){
        //logger.info("MMS850MI AddReclass Response = ${response}")
        reclassified = true
      }
      
    }
    miCaller.call("MMS850MI","AddReclass", params, callback)
    return reclassified
  }
  String EXT700MIAddProc(String panr, String msg1, String msg2, String grwe, String stat){
     String msg = null
     Map<String,String> params= ["CONO":"${xxcono}".toString(),
                  "PANR": "${panr}".toString(),
                  "MSG1": "${msg1}".toString(),
                  "MSG2": "${msg2}".toString(),
                  "GRWE": "${grwe}".toString(),
                  "STAT": "${stat}".toString()
                 ]
     def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("EXT700MI Add Error Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
        msg = "FAILED"
      }
      else{
        msg = response.MSG1.trim()
      }
       
    }
    miCaller.call("EXT700MI","Add", params, callback)
    return msg
  }
  boolean EXT700MIUpdateproc(String panr, String msg1, String msg2, String grwe, String stat){
    boolean respok = true
    Map<String,String> params= ["CONO":"${xxcono}".toString(),
                  "PANR": "${panr}".toString(),
                  "MSG1": "${msg1}".toString(),
                  "MSG2": "${msg2}".toString(),
                  "GRWE": "${grwe}".toString(),
                  "STAT": "${stat}".toString()
                 ]
    def callback = {
      Map<String, String> response ->
      if(response.errorMessage != null){
        //logger.info("EXT700MI Update Error Response = ${response.errorMessage}")
        mi.error(response.errorMessage)
        respok = false
       }
    }
    miCaller.call("EXT700MI","Update", params, callback)
    return respok
  }
 
 /*
  * Read PO Delivery MPLIND
  *
  */
 
  Closure<?> poDelivery = { DBContainer container ->
    puno = container.get("ICPUNO")
    pnli = container.get("ICPNLI")
    pnls = container.get("ICPNLS")
    pnlx = container.get("ICPNLX")
    Integer poStatus = poStatusProc()
    if (poStatus < 75){
      return
    }
  }
  
  /*
  * Read attribute values MIATTR
  *
  */
  Closure<?> getAttribValues = { DBContainer container ->
    String agatid = container.get("AGATID")
    String agatva = container.get("AGATVA")
    if (agatid.trim() == "REC04"){ //If attribute id is carrier
      carrier = agatva.trim()
    }
    if(agatid.trim() == "REC05"){ //If attribute id is loader
      loader = agatva.trim()
    }
    if(agatid.trim() == "DRF01"){ //If attribute id is fruitgrade
      fruitgrade = agatva.trim()
    }
  }
  
  /*
  * Read PO Lines MPLINE
  *
  */
  Closure<?> polinesadded = { DBContainer container ->
        pnli = container.get("IBPNLI") // The last line read will be the newly added line
  }
  
  
  /*
  * Read  PMS002 MPDMAT
  *
  */
  Closure<?> newitems = { DBContainer container ->
        newitem = container.get("PMMTNO")
  }
} 
