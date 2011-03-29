package org.psug.usi.domain

import org.psug.usi.store._
import com.sleepycat.bind.tuple.{IntegerBinding, StringBinding}
import com.sleepycat.je.{LockMode, DatabaseEntry}

import com.sun.jersey.core.util.Base64

object AuthenticationToken {
  val re = "(\\d+);(.*)".r
  implicit def decrypt(value : String) : AuthenticationToken = Base64.base64Decode(value) match {
    case re(id,mail) => AuthenticationToken(Integer.parseInt(id),mail)
    case _            => throw new IllegalArgumentException("cannot decode token "+value)
  }

  implicit def encrypt(token : AuthenticationToken) : String = new String(Base64.encode(token.id + ";" + token.mail))
}

case class AuthenticationToken(id : Int, mail : String)
case class Credentials(mail: String, password: String)

object UserVO {
  var index=0
  def create_id={
    index++
    index
  }
}
case class UserVO( firstname:String, lastname:String, mail:String, password:String )  {
  def id:Int=
  def getUser()=User(id, firstname, lastname, mail, password)
}
object User{
    def apply(firstname : String, lastname : String, mail : String, password : String):User = User( 0, firstname, lastname, mail, password )
}
case class User( id:Int, firstname:String, lastname:String, mail:String, password:String ) extends Data[Int] with Ordered[User] {
  def storeKey:Int = id
  def copyWithAutoGeneratedId( id:Int ) = User( id, firstname, lastname, mail, password )

  def compare(that: User) ={
    val c1 = that.firstname compare firstname
    if( c1 == 0 ){
      val c2 = that.lastname compare lastname
      if( c2 == 0 ) {
        val c3 = that.mail compare mail
        if( c3 == 0 ) that.id compare id
        else c3
      } else c2
    } else c1
  }

  /*
    les longueurs min/max sont les suivantes : de 2 à 50 caractères pour nom, prénom, mail et password.
   */
  def isValid = {
    firstname.length >= 2 && firstname.length <= 50 &&
    lastname.length >= 2 && lastname.length <= 50 &&
    mail.length >= 2 && mail.length <= 50 &&
    password.length >= 2 && password.length <= 50
  }

}

case class PullDataByEmail( mail : String ) extends DataRepositoryMessage
case class AuthenticateUser (credentials : Credentials) extends DataRepositoryMessage
case class UserAuthenticated (user : Either[User,String]) extends DataRepositoryMessage

abstract class UserRepository extends BDBDataRepository[Int,User]( "UserRepository", new BDBSimpleDataFactory[User] ) {

  def incrementAndGetCurrentId:Int = { currentId += 1 ; currentId }
  def currentIdResetValue = 0


  // TODO: should be nested TX
  override def store(data:User) = {
    val copyData = data.copyWithAutoGeneratedId(incrementAndGetCurrentId)
    (checkConstraint(copyData)) match {
      case None =>

        val key = new DatabaseEntry()
        StringBinding.stringToEntry( copyData.mail, key )
        val data = new DatabaseEntry()
        IntegerBinding.intToEntry( copyData.id, data )
        database.put( null, key, data )

        save(copyData)
        Right(copyData)
      case Some(message) =>
        Left("Invalid store contraint: " + message)
    }
  }

  def idByEmail( mail:String ) = {
    val key = new DatabaseEntry()
    StringBinding.stringToEntry( mail, key ) 
    val data = new DatabaseEntry()
    database.get( null, key, data, LockMode.READ_UNCOMMITTED )
    if( data.getData != null ){
      Some( IntegerBinding.entryToInt( data ) )
    }
    else None
  }


  /*
    si un utilisateur ayant la même adresse mail existe déjà, une erreur est retournée.
   */
  override protected def checkConstraint( user:User )={
    if(!user.isValid)
      Some("User does not respect fields format constraint")
    else if(!idByEmail( user.mail ).isEmpty)
      Some("A user with same mail as "+ user.mail +" is registered")
    else
      None
  }

  def lookupUser(mail : String) : DataPulled[Int] =
    DataPulled[Int]( for(id   <- idByEmail( mail );
                    user <- load( id )) yield user)

  override def handleMessage( any:Any )={
    any match {
      case PullDataByEmail( mail ) =>
        lookupUser(mail)
      case AuthenticateUser(credentials) =>
        lookupUser(credentials.mail) match {
          case DataPulled(Some(user))  =>  {
            if(user.asInstanceOf[User].password == credentials.password) {
              UserAuthenticated(Left(user.asInstanceOf[User]))
            } else
              UserAuthenticated(Right("invalid credentials for user " + credentials.mail))
          }
          case DataPulled(None)       =>
              UserAuthenticated(Right("invalid credentials for user " + credentials.mail))
        }
      case _ => super.handleMessage( any )
    }
  }
}

