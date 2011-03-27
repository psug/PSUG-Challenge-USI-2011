package org.psug.usi

import org.psug.usi.netty.WebServer
import service.{RemoteServices, SimpleRepositoryServices}

/**
 * 
 * @author abailly@oqube.com
 * @version $Rev$
 */

/**
 * Aslo check resource/akka.conf for akka server configuration
 * We can start manual the akka remote actor server using
 * remote.start("localhost", 2552)
 * see http://doc.akka.io/remote-actors-scala
 */

object Main {

  val WEB_AUTHICATION_KEY = "dummy"
  val DEFAULT_PORT = "8082"

  def main(args : Array[String]) = {
    val port = if(args.length > 0) args(0) else DEFAULT_PORT

    val server = new WebServer(Integer.parseInt(port))
    server.start
    while(System.in.read() == -1)
      wait(500)
    server.stop
  }

}

class Main {

  trait Agent  {
    val name  : String
    val port  : Int
    def start : Unit
    def stop  : Unit
  }

  var agent : Agent = null

  def start(args : String*) = {
    val webport : Int = Integer.parseInt(args(1))
    args(0) match {
      case "Web" =>
        val servicesPort : Int = Integer.parseInt(args(2))
        agent = new WebServer(webport,new RemoteServices(servicesPort)) with Agent {
          val name =  "Web"
          val port = webport
        }
      case "Service" =>
        agent = new SimpleRepositoryServices(webport) with Agent {
          val name = "Services"
          val port = webport
        }
    }
    agent.start
    println("Started PSUG USI2011 Challenge " + agent.name  +" agent  at 0.0.0.0:" + agent.port)
  }

  def stop() = {
    agent.stop
    println("Stopped PSUG USI2011 Challenge " + agent.name + " at 0.0.0.0:" + agent.port)
  }

}
