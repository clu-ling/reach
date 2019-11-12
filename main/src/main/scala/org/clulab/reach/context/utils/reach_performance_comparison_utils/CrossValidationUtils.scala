package org.clulab.reach.context.utils.reach_performance_comparison_utils

import org.clulab.context.classifiers.{DummyClassifier, LinearSVMContextClassifier}
import org.clulab.context.utils.AggregatedContextInstance
import org.clulab.reach.context.utils.score_utils.ScoreMetricsOfClassifier

import scala.collection.immutable.ListMap

object CrossValidationUtils {

  def performCVOnSelectedPapers(pathToUntrainedSVMInstance:String, rowsOfAggrRows:Seq[AggregatedContextInstance], papersToExclude:Option[List[String]]=None):(Double,Double,ListMap[String,Double])={



    val svmWrapper = new LinearSVMContextClassifier()
    val untrainedInstanceForCV = svmWrapper.loadFrom(pathToUntrainedSVMInstance)
    val classifierToCheckForNull = untrainedInstanceForCV.classifier match {
      case Some(x) => x
      case None => {
        null
      }
    }

    if(classifierToCheckForNull == null) throw new NullPointerException("No classifier found on which I can predict. Please make sure the SVMContextEngine class receives a valid Linear SVM classifier.")

    println(s"The SVM model has been tuned to the following settings: C: ${classifierToCheckForNull.C}, Eps: ${classifierToCheckForNull.eps}, Bias: ${classifierToCheckForNull.bias}")
    val availablePaperIDs = rowsOfAggrRows.map(x => x.PMCID).toSet
    val papersToUseForCV = papersToExclude match {
      case Some(x) => availablePaperIDs.filter(a => !x.contains(a))
      case None => availablePaperIDs
    }

    val microAveragedTrueLabels = collection.mutable.ListBuffer[Int]()
    val microAveragedPredictedLabels = collection.mutable.ListBuffer[Int]()
    var totalAccuracy = 0.0
    val perPaperAccuracyMap = collection.mutable.HashMap[String,Double]()
    println(s"Papers being used for CV")
    println(papersToUseForCV.mkString(","))
    for(paperID <- papersToUseForCV){

      val testingRowsFromCurrentPaper = rowsOfAggrRows.filter(x=>x.PMCID == paperID)
      val trainingRows = rowsOfAggrRows.filter(x=>x.PMCID!=paperID)

      val trainingfeatureValues = untrainedInstanceForCV.constructTupsForRVF(trainingRows)
      val trainingLabels = DummyClassifier.getLabelsFromDataset(trainingRows)
      val (trainingDataset,_) = untrainedInstanceForCV.mkRVFDataSet(trainingLabels.toArray,trainingfeatureValues)
      untrainedInstanceForCV.fit(trainingDataset)

      val testingLabels = DummyClassifier.getLabelsFromDataset(testingRowsFromCurrentPaper)
      val predictedValuesPerTestFold = untrainedInstanceForCV.predict(testingRowsFromCurrentPaper)
      microAveragedTrueLabels ++= testingLabels
      microAveragedPredictedLabels ++= predictedValuesPerTestFold

      val accuracyPerPaper = ScoreMetricsOfClassifier.accuracy(testingLabels.toArray, predictedValuesPerTestFold)

      totalAccuracy += accuracyPerPaper
      perPaperAccuracyMap ++= Map(paperID -> accuracyPerPaper)
    }
    val microAveragedAccuracy = ScoreMetricsOfClassifier.accuracy(microAveragedTrueLabels, microAveragedPredictedLabels)

    val arithmeticMeanAccuracy = totalAccuracy/papersToUseForCV.size



    val sortedPerPaperAccuracyMap = ListMap(perPaperAccuracyMap.toSeq.sortWith(_._2>_._2):_*)


    (microAveragedAccuracy, arithmeticMeanAccuracy, sortedPerPaperAccuracyMap)
  }

  def getBestFeatureSet(allFeatures:Seq[String]):Seq[String] = {
    val nonNumericFeatures = Seq("PMCID", "label", "EvtID", "CtxID", "")
    val numericFeatures = allFeatures.toSet -- nonNumericFeatures.toSet
    val featureDict = CrossValidationUtils.createFeaturesLists(numericFeatures.toSeq)
    val bestFeatureSet = featureDict("NonDep_Context")
    bestFeatureSet
  }

  def extractDataByRelevantFeatures(featureSet:Seq[String], data:Seq[AggregatedContextInstance]):Seq[AggregatedContextInstance] = {
    val result = data.map(d => {
      val currentSent = d.sentenceIndex
      val currentPMCID = d.PMCID
      val currentEvtId = d.EvtID
      val currentContextID = d.CtxID
      val currentLabel = d.label
      val currentFeatureName = d.featureGroupNames
      val currentFeatureValues = d.featureGroups
      val indexList = collection.mutable.ListBuffer[Int]()
      // we need to check if the feature is present in the current row. Only if it is present should we try to access its' value.
      // if not, i.e. if the feature is not present and we try to access it, then we get an ArrayIndexOutOfBound -1 error/
      featureSet.map(f => {
        if(currentFeatureName.contains(f)) {
          val tempIndex = currentFeatureName.indexOf(f)
          indexList += tempIndex
        }
      })
      val valueList = indexList.map(i => currentFeatureValues(i))
      AggregatedContextInstance(currentSent, currentPMCID, currentEvtId, currentContextID, currentLabel, valueList.toArray, featureSet.toArray)
    })
    result
  }

  def createFeaturesLists(numericFeatures: Seq[String]):Map[String, Seq[String]] = {
    val contextDepFeatures = numericFeatures.filter(_.startsWith("ctxDepTail"))
    val eventDepFeatures = numericFeatures.filter(_.startsWith("evtDepTail"))
    val nonDepFeatures = numericFeatures.toSet -- (contextDepFeatures.toSet ++ eventDepFeatures.toSet)
    val map = collection.mutable.Map[String, Seq[String]]()
    map += ("All_features" -> numericFeatures)
    map += ("Non_Dependency_Features" -> nonDepFeatures.toSeq)
    map += ("NonDep_Context" -> (nonDepFeatures ++ contextDepFeatures.toSet).toSeq)
    map += ("NonDep_Event" -> (nonDepFeatures ++ eventDepFeatures.toSet).toSeq)
    map += ("Context_Event" -> (contextDepFeatures.toSet ++ eventDepFeatures.toSet).toSeq)
    map.toMap
  }
}