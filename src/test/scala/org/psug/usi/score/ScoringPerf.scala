package org.psug.usi.score

import org.junit._
import java.util.Random
import org.apache.commons.math.stat.StatUtils.{mean,variance}

trait PerfUtilities {

  val random = new Random(System.nanoTime)

  /**
   * Compute standard deviation from variance.
   */
  def stddev(used : Array[Double]) : Double = Math.sqrt(variance(used))

  implicit def asArray[A : ClassManifest](s : IndexedSeq[A]) : Array[A] = s.toArray

  /**
   * Provide the execution time of the suspension given in parameter.
   */
  def time[A](f : => A) : Double = {
      val start = System.nanoTime
      f
      System.nanoTime - start
  }
  
  def simulateAnswers(numQuestions : Int, correctAnswerProbability: Double, users : Array[UserResponseAgent]) = {
    for(j <- 1 to numQuestions) 
      for(i <- 0 to users.length-1) 
	  if(random.nextDouble < correctAnswerProbability) users(i).ok 
  }
}

/**
 * Generates performance profiles for the scoring agents.
 * This class runs a single test repeatedly with various arguments to produce an
 * execution profile of the scoring system under various loads. The tests are run
 * following this pattern:
 * <ul>
 * <li>run the test 5 times with medium number of users without recording result,</li>
 * <li>then run the test 30 times for each user number,</li>
 * <li>for each shot, score randomly 10 "questions" with each user having a 1/2 probability of answering a question,</li>
 * <li>record the mean and standard deviation for each point,</li>
 * </ul>
 */
class ScoringPerf extends PerfUtilities{

  val numberOfUsers = List(10,50,100,500,1000,5000)

  @Test
  def runPerformanceTests = {
    runWithNumberOfUsers(1000)
    for(num <- numberOfUsers) {
      val (mean,stddev) = runWithNumberOfUsers(num)
      println(num + ": mean="+ (mean/1000000).asInstanceOf[Long] + ", stddev=" +(stddev/1000000).asInstanceOf[Long])
    }
  }

  def runWithNumberOfUsers(num : Int) :(Double,Double)= {
    val scorer = new Scorer(num)(10)
    val users = new Array[UserResponseAgent](num)
    scorer.start
    for (i <- 0 to num-1) { users(i) = new UserResponseAgent(i,scorer) }
    val values = for(j <- 1 to 30) yield time(singleTest(num,users))
    (mean(values), stddev(values))
  }

  def singleTest(num : Int,users : Array[UserResponseAgent]) : Unit = {
      for(j <- 1 to 10) 
	for(i <- 0 to num-1) 
	  if(random.nextDouble < 0.5) users(i).ok 
    }
}
