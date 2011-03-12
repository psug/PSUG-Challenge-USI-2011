package org.psug.usi.domain

import org.psug.usi.store.{BDBDataFactory, BDBSimpleDataFactory, BDBDataRepository, Data}
import com.sleepycat.je.DatabaseEntry
import java.io.{ByteArrayOutputStream, ObjectOutputStream, ByteArrayInputStream, ObjectInputStream}
import com.sleepycat.bind.tuple.{TupleOutput, TupleInput}
import xml.XML

/**
 * User: alag
 * Date: 2/16/11
 * Time: 11:09 PM
 */

case class CreateGame( authentication_key:String, parameters:String )

/*
 </usi:gamesession>
  <usi:questions>
    <usi:question goodchoice="0">
      <usi:label>usi:label</usi:label>
      <usi:choice>usi:choice</usi:choice>
      <usi:choice>usi:choice</usi:choice>
      <usi:choice>usi:choice</usi:choice>
      <usi:choice>usi:choice</usi:choice>
    </usi:question>
  </usi:questions>

  <usi:parameters>
    <usi:logintimeout>0</usi:logintimeout>
    <usi:synchrotime>0</usi:synchrotime>
    <usi:nbusersthreshold>0</usi:nbusersthreshold>
    <usi:questiontimeframe>0</usi:questiontimeframe>
    <usi:nbquestions>0</usi:nbquestions>
    <usi:flushusertable>true</usi:flushusertable>
    <usi:trackeduseridmail>usi:trackeduseridmail</usi:trackeduseridmail>
  </usi:parameters>


logintimeout : durée maximale du long polling (GET /api/question/1) en secondes. Au bout de cette durée, le serveur répond à toutes les requêtes en attente, si il ne l'a pas déjà fait. Voir la page de séquences.
synchrotime : durée entre la fin de la période de réponse pour une question N (POST /api/answer/N) et le retour synchrone des appels à une question N+1 (GET /api/question/N+1) en secondes. Voir la page de séquences.
nbusersthreshold : le nombre d'utilisateurs qui doit être atteint pour que la partie commence.
questiontimeframe : à partir du moment où le serveur répond aux requêtes question, les utilisateurs ont un temps maximum pour répondre à la question. Ce paramètre indique cette durée en secondes. Voir la page de séquences.
nbquestions : le nombre de questions à jouer. Doit être inférieur ou égal au nombre de questions présentes dans le fichier (élement <usi:questions>).
flushusertable : un booleen indiquant si toutes les données utilisateurs (liste des utilisateurs, scores et historiques) doivent être supprimées.
trackeduseridmail : ne pas tenir compte de ce paramètre.

"La valeur d'une question n (Valeur(n)) est 1 pour les 5 premières questions, 5 pour les questions 6 à 10, 10 pour les questions 11 à 15, 15 pour les questions 16 à 20"
*/

object Game{
  def apply( xmlParameters:String ) = {
    val xml = XML.loadString( xmlParameters )

    val questions = ( xml \ "questions" \\ "question").zipWithIndex.map {
      case ( xmlQuestion, index )  =>
        val goodChoice =  ( xmlQuestion \ "@goodchoice" ).text.toInt
        val answers = ( xmlQuestion \\ "choice" ).zipWithIndex.map {
          case ( xmlAnwer, index ) => Answer( xmlAnwer.text, index == goodChoice )
        }

        var questionValue = ((index)/5)*5
        if( questionValue == 0 ) questionValue = 1
        Question( ( xmlQuestion \ "label" ).text, answers, questionValue )

    }

    new Game( 0,
              questions,
              loginTimeoutSec = (xml \ "parameters" \ "logintimeout").text.toInt,
              synchroTimeSec = (xml \ "parameters" \ "synchrotime").text.toInt,
              questionTimeFrameSec = (xml \ "parameters" \ "questiontimeframe").text.toInt,
              nbQuestions = (xml \ "parameters" \ "nbquestions").text.toInt,
              flushUserTable = (xml \ "parameters" \ "flushusertable").text.toBoolean,
              nbUsersThreshold = (xml \ "parameters" \ "nbusersthreshold").text.toInt
    )
  }
}
case class Game( id : Int=0, questions:Seq[Question], loginTimeoutSec:Int=10, synchroTimeSec:Int=10, questionTimeFrameSec:Int=10, nbQuestions:Int, flushUserTable:Boolean=false, nbUsersThreshold:Int=0 ) extends Data[Int]{
  require( nbQuestions <= questions.size )
  def storeKey:Int = id
  def copyWithAutoGeneratedId( id:Int ) = Game( id, questions, loginTimeoutSec, synchroTimeSec, questionTimeFrameSec, nbQuestions, flushUserTable, nbUsersThreshold )
}

case class Question( question:String, answers:Seq[Answer], value:Int )

case class Answer( anwser:String, status:Boolean )




abstract class GameRepository extends BDBDataRepository[Int,Game]( "GameRepository",  new BDBSimpleDataFactory[Game] ){
  
  def incrementAndGetCurrentId:Int = { currentId += 1 ; currentId }
  def currentIdResetValue = 0

}









case class AnswerHistory( questionIndex:Int, answerIndex:Int )
case class GameUserKey( gameId:Int, userId:Int )
case class GameUserHistory( gameUser:GameUserKey, anwsers:List[AnswerHistory]) extends Data[GameUserKey]{
    def storeKey:GameUserKey = gameUser
    def copyWithAutoGeneratedId( id:GameUserKey ) = this
}


class BDBGameUserHistoryFactory extends BDBDataFactory[GameUserKey,GameUserHistory]{
  def entryToValue( entry:DatabaseEntry ):Option[GameUserHistory] ={
    val buffer = entry.getData
    if (buffer != null) {
      val ois = new ObjectInputStream(new ByteArrayInputStream(buffer))
      Some(ois.readObject().asInstanceOf[GameUserHistory])
    }
    else None
  }

  def entryToKey( entry:DatabaseEntry ):Option[GameUserKey] = {
    if( entry.getData != null ){
        val ti  = new TupleInput( entry.getData )
        Some( GameUserKey( ti.readInt, ti.readInt ) )
    }
    else None
  }

  def keyToEntry( key:GameUserKey ):DatabaseEntry={
    val to = new TupleOutput()
    to.writeInt( key.gameId )
    to.writeInt( key.userId )
    val entry = new DatabaseEntry( to.getBufferBytes )
    entry
  }
    
  def valueToEntry( value:GameUserHistory ):DatabaseEntry={
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(value)
    new DatabaseEntry(baos.toByteArray)
  }
}

abstract class GameUserHistoryRepository extends BDBDataRepository[GameUserKey,GameUserHistory]( "GameUserHistoryRepository",  new BDBGameUserHistoryFactory ){
  def incrementAndGetCurrentId:GameUserKey = { currentId }
  def currentIdResetValue = GameUserKey(0,0)
}
