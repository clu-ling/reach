package org.clulab.reach.context.context_exec
import java.io.{File, FileOutputStream, ObjectOutputStream, PrintWriter}

import ai.lum.nxmlreader.NxmlReader
import com.typesafe.config.ConfigFactory
import org.clulab.odin.EventMention
import org.clulab.reach.PaperReader.{contextEngineParams, ignoreSections, preproc, procAnnotator}
import org.clulab.reach.ReachSystem
import org.clulab.reach.context.ContextEngineFactory.Engine
import org.clulab.reach.mentions.{BioEventMention, BioTextBoundMention}
object Polarity2 extends App{
  val config = ConfigFactory.load()
  val papersDir = config.getString("papersDir")
  val nxmlReader = new NxmlReader(ignoreSections.toSet, transformText = preproc.preprocessText)
  val contextEngineType = Engine.withName(config.getString("contextEngine.type"))
  lazy val reachSystem = new ReachSystem(processorAnnotator = Some(procAnnotator),
    contextEngineType = contextEngineType,
    contextParams = contextEngineParams)
  val fileListUnfiltered = new File(papersDir)
  val fileList = fileListUnfiltered.listFiles().filter(x => x.getName.endsWith(".nxml"))
  val egfDiffEvents = collection.mutable.ListBuffer[BioEventMention]()
  val dirForOutput = config.getString("polarityContext.contextLabelsOutputDir")
  for(file <- fileList) {
    val nxmlDoc = nxmlReader.read(file)
    val document = reachSystem.mkDoc(nxmlDoc)
    val mentions = reachSystem.extractFrom(document)
    val evtMentionsOnly = mentions.collect { case evt: BioEventMention => evt }
    for(ev <- evtMentionsOnly) {
      val sentenceID = ev.sentence
      val tokenInterval = ev.tokenInterval
      val subsentence = ev.document.sentences(sentenceID).words.slice(tokenInterval.start,tokenInterval.end+1).mkString(" ")
      if (checkAddingCondition(subsentence))
        {egfDiffEvents += ev
        println(subsentence)}
    }
  }

  val egfDiffEventsWithContext = egfDiffEvents.filter(_.hasContext())
  println(s"Number of events with associated context: ${egfDiffEventsWithContext.size}")

  val activationContextLabels = collection.mutable.ListBuffer[String]()
  val inhibitionContextLabels = collection.mutable.ListBuffer[String]()
  val paperByContextLabelsMap = collection.mutable.HashMap[String, Array[String]]()
  val eventsByPaperIDMap = collection.mutable.HashMap[String, collection.mutable.ListBuffer[BioEventMention]]()
  for(e <- egfDiffEventsWithContext) {
    val contextLabels = e.context match {
      case Some(x) => x
      case None => Map.empty
    }

    val contextLabelsInTheCurrentEvent = collection.mutable.ListBuffer[String]()
      contextLabels.map(x => {
        contextLabelsInTheCurrentEvent ++= x._2})

    if(e.label.contains("Positive")) activationContextLabels ++= contextLabelsInTheCurrentEvent
    else if(e.label.contains("Negative")) inhibitionContextLabels ++= contextLabelsInTheCurrentEvent
    val docID = e.document.id match {
      case Some(s) => s"PMC${s.split("_")(0)}"
      case None => "Unknown"
    }

    val entry = Map(docID -> contextLabelsInTheCurrentEvent.toArray)
    paperByContextLabelsMap ++= entry

    if(eventsByPaperIDMap.contains(docID)) {
      val currentList = eventsByPaperIDMap(docID)
      currentList += e
    }
    else {
      val newList = collection.mutable.ListBuffer[BioEventMention]()
      newList += e
      eventsByPaperIDMap ++= Map(docID -> newList)
    }


  }

  for((paperID,eventsPerPaper) <- eventsByPaperIDMap) {
    val perPaperDir = dirForOutput.concat(paperID)
    println(perPaperDir)
    val outputPaperDir = new File(perPaperDir)
    if(!outputPaperDir.exists()) {
      outputPaperDir.mkdirs()
    }
    val eventsPath = perPaperDir.concat("/ArrayOfEvtsByPaper.txt")
    val eventsFile = new File(eventsPath)
    if (!eventsFile.exists()) {
      eventsFile.createNewFile()
    }

    val printWriter = new PrintWriter(eventsFile)
    val listOfEventIds = eventsPerPaper.map(ex => extractEvtId(ex))
    val str = "".concat(listOfEventIds.mkString(","))
    printWriter.write(str)


    printWriter.close()
  }
  for((paperID, contextLabels) <- paperByContextLabelsMap) {
    val perPaperDir = dirForOutput.concat(paperID)
    val outputPaperDir = new File(perPaperDir)
    if(!outputPaperDir.exists()) {
      outputPaperDir.mkdirs()
    }
    val contextFilePath = perPaperDir.concat("/contextLabelsPerPaper.txt")
    val contextFile = new File(contextFilePath)
    if (!contextFile.exists()) {
      contextFile.createNewFile()
    }

    val printwriter = new PrintWriter(contextFile)
    val str = "".concat(contextLabels.mkString(","))
    printwriter.write(str)
    println(s"The paper ${paperID} has the context labels ${str}")
    printwriter.close()
  }

  val pathForActivationLabels = dirForOutput.concat("activationContextLabels.txt")
  val activationLabelsFile = new File(pathForActivationLabels)
  val pathForInhibitionLabels = dirForOutput.concat("inhibitionContextLabels.txt")
  val inhibitionLabelsFile = new File(pathForInhibitionLabels)
  if (!activationLabelsFile.exists()) {
    activationLabelsFile.createNewFile()
  }
  if (!inhibitionLabelsFile.exists()) {
    inhibitionLabelsFile.createNewFile()
  }

  val actPrintWriter = new PrintWriter(activationLabelsFile)
  val inhPrintWriter = new PrintWriter(inhibitionLabelsFile)

  val actString = "".concat(activationContextLabels.mkString(","))
  val inhString = "".concat(inhibitionContextLabels.mkString(","))

  actPrintWriter.write(actString)
  inhPrintWriter.write(inhString)

  actPrintWriter.close()
  inhPrintWriter.close()

  def checkAddingCondition(sentence: String):Boolean = {
    checkEGFcase(sentence) && checkDifferentCase(sentence)
  }

  def checkEGFcase(sentence:String):Boolean = {
    sentence.contains("EGF") || sentence.contains("egf") || sentence.contains("Epidermal Growth Factor") || sentence.contains("Epidermal growth factor") || sentence.contains("epidermal growth factor")
  }

  def checkDifferentCase(sentence:String):Boolean = {
    sentence.contains("differentiation") || sentence.contains("Differentiation") || sentence.contains("cell differentiation") || sentence.contains("Cell differentiation") || sentence.contains("cell-differentiation")
  }

  type Pair = (BioEventMention, BioTextBoundMention)
  type EventID = String
  type ContextID = (String, String)
  def extractEvtId(evt:BioEventMention):EventID = {
    val sentIndex = evt.sentence
    val tokenIntervalStart = (evt.tokenInterval.start).toString()
    val tokenIntervalEnd = (evt.tokenInterval.end).toString()
    sentIndex+tokenIntervalStart+tokenIntervalEnd
  }


}
