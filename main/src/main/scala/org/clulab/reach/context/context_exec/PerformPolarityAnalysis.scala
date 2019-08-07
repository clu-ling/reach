package org.clulab.reach.context.context_exec

import java.io.{File, FileInputStream, ObjectInputStream}

import scala.io
import com.typesafe.config.ConfigFactory
import org.clulab.reach.mentions.BioEventMention

import scala.io.Source

object PerformPolarityAnalysis extends App {

  val config = ConfigFactory.load()
  val operatingDirPath = config.getString("polarityContext.contextLabelsOutputDir")
  val operatingDirFile = new File(operatingDirPath)
  val paperDirs = operatingDirFile.listFiles().filter(_.isDirectory)
  val contextsPerPaperMap = collection.mutable.HashMap[String, collection.mutable.ListBuffer[String]]()
  val activationLabelsNonUnique = collection.mutable.ListBuffer[String]()
  val inhibitionLabelsNonUnique = collection.mutable.ListBuffer[String]()
  for(pDir <- paperDirs) {
    val contextsFiles = pDir.listFiles().filter(x => x.getName().contains("ContextsForEvent"))
    for(contextsFile <- contextsFiles) {
      val contextFileName = contextsFile.getName()
      val polarity1 = contextFileName.split("_")(2)
      val polarity = polarity1.slice(0, polarity1.length - 4)
      val paperID = pDir.getName()
      val fileContents = Source.fromFile(contextsFile).getLines().toSeq
      val labelsTemp = fileContents(0).split(",")
      val labels = collection.mutable.ListBuffer[String]()
      labelsTemp.map(x => labels += x)
      if(contextsPerPaperMap.contains(paperID)) {
        val currentList = contextsPerPaperMap(paperID)
        currentList ++= labels
      }
      else {
        val entry = Map(paperID -> labels)
        contextsPerPaperMap ++= entry
      }

      println(s"The name of the labels file is: ${contextFileName}")
      println(s"The polarity is: ${polarity}")
      if(polarity == "activation") activationLabelsNonUnique ++= labels
      else inhibitionLabelsNonUnique ++= labels
    }
  }
  val allNonUniqueLabels = collection.mutable.ListBuffer[String]()
  allNonUniqueLabels ++= activationLabelsNonUnique
  allNonUniqueLabels ++= inhibitionLabelsNonUnique
  val frequencyOfAllNonUniqueLabels = countLabelFrequencyInList(allNonUniqueLabels.toArray)
  val parentPapersCountAllNonUniqueLabels = countPapersUsingLabelsInList(allNonUniqueLabels.toArray, contextsPerPaperMap.toMap)
  val uniqueActivationIntersectIncluded = activationLabelsNonUnique.toSet
  val uniqueInhibitionIntersectIncluded = inhibitionLabelsNonUnique.toSet
  val commonLabels = uniqueActivationIntersectIncluded.intersect(uniqueInhibitionIntersectIncluded)
  val exclusivelyActivation = uniqueActivationIntersectIncluded -- commonLabels
  val exclusivelyInhibition = uniqueInhibitionIntersectIncluded -- commonLabels


  println("\n ****** Printing non-unique activation labels *********")

  println(activationLabelsNonUnique.mkString(","))

  println("\n ******* Printing non-unique inhibition labels ********")
  println(inhibitionLabelsNonUnique.mkString(","))

  println("\n ****** PRINTING COMMON LABELS ******")
  commonLabels.map(println)




  println(s"\n ************ There are totally ${exclusivelyActivation.size} unique activation labels, and they are:  ************ ")
  for(excAct <- exclusivelyActivation) {
    val noOfOccurrences = frequencyOfAllNonUniqueLabels(excAct)
    println(s"The unique activation label ${excAct} appears totally ${noOfOccurrences} times")
    val papersUsingThisLabel = parentPapersCountAllNonUniqueLabels(excAct)
    println(s"${papersUsingThisLabel._1} papers use this label, and they are: ${papersUsingThisLabel._2.mkString(",")}")
  }



  println(s"\n ************ There are totally ${exclusivelyInhibition.size} unique inhibition labels, and they are:  ************ ")
  for(excInh <- exclusivelyInhibition) {
    val noOfOccurrences = frequencyOfAllNonUniqueLabels(excInh)
    println(s"The unique inhibition label ${excInh} appears totally ${noOfOccurrences} times")
    val papersUsingThisLabel = parentPapersCountAllNonUniqueLabels(excInh)
    println(s"${papersUsingThisLabel._1} papers use this label, and they are: ${papersUsingThisLabel._2.mkString(",")}")
  }



  def countLabelFrequencyInList(listOfLabels:Array[String]):Map[String, Int] = {
    val toReturn = collection.mutable.HashMap[String,Int]()
    for(l <- listOfLabels) {
      if(toReturn.contains(l)) {
        var existingCount = toReturn(l)
        existingCount += 1
        toReturn(l) = existingCount
      }

      else {
        val entry = Map(l -> 1)
        toReturn ++= entry
      }
    }
    toReturn.toMap
  }



  def countPapersUsingLabelsInList(listOfLabels: Array[String], mapOfLabelsInPaper:Map[String,collection.mutable.ListBuffer[String]]):Map[String,(Int, Array[String])] = {
    val mapToReturn = collection.mutable.HashMap[String,(Int, Array[String])]()
    for(l <- listOfLabels) {
      val paperlist = collection.mutable.ListBuffer[String]()
      var papercount = 0
      for((paperID,listOfLabelsInPaper) <- mapOfLabelsInPaper) {
        if(listOfLabelsInPaper.contains(l)) {
          papercount +=1
          paperlist += paperID
        }
      }

      val tup = (papercount, paperlist.toArray)
      val entry = Map(l -> tup)
      mapToReturn ++= entry
    }
    mapToReturn.toMap
  }

}
